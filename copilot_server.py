"""
SmartFactory Vision — AI Copilot Server (Phase 7)
LangChain + Gemini 기반 공장 AI 비서
포트: 8765
"""

import os
import sys
from pathlib import Path
from dotenv import load_dotenv

# .env 로드 (실행 위치에 따라 경로 조정)
_env_path = Path(__file__).parent.parent.parent.parent.parent / "SmartFactory-Vision" / ".env"
if _env_path.exists():
    load_dotenv(_env_path)
else:
    # fallback: 현재 디렉토리
    load_dotenv()

import requests
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# --- LangChain / Google Gemini Imports ---
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_community.vectorstores import FAISS
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import TextLoader
from langchain.tools import Tool
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate
from google.api_core.exceptions import ResourceExhausted
from langchain import hub

# -----------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:9090")
PORT = int(os.getenv("COPILOT_SERVER_PORT", "8765"))
MANUAL_PATH = Path(__file__).parent / "factory_manual.txt"

print(f"[Copilot] API Key loaded: {'YES' if GEMINI_API_KEY else 'NO - check .env'}", flush=True)
print(f"[Copilot] Spring Backend: {SPRING_BASE_URL}", flush=True)

# -----------------------------------------------------------------------
# FastAPI App
# -----------------------------------------------------------------------
app = FastAPI(title="SmartFactory AI Copilot", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class ChatRequest(BaseModel):
    question: str
    session_id: str = "default"

class ChatResponse(BaseModel):
    answer: str
    tool_used: str = "general"
    error: str = ""

# -----------------------------------------------------------------------
# Global agent (lazy-initialized on first request)
# -----------------------------------------------------------------------
_agent_executor = None
_vectorstore = None

def get_manual_chunks():
    global _vectorstore
    if _vectorstore is not None:
        return _vectorstore

    print(f"[Copilot] Loading manual chunks from: {MANUAL_PATH}", flush=True)
    if not MANUAL_PATH.exists():
        print("[Copilot] WARNING: factory_manual.txt not found!", flush=True)
        _vectorstore = ["SmartFactory Vision 매뉴얼 파일을 찾을 수 없습니다. 관리자에게 문의하세요."]
        return _vectorstore

    loader = TextLoader(str(MANUAL_PATH), encoding="utf-8")
    raw_docs = loader.load()
    splitter = RecursiveCharacterTextSplitter(chunk_size=800, chunk_overlap=100)
    docs = splitter.split_documents(raw_docs)
    _vectorstore = [d.page_content for d in docs]
    print(f"[Copilot] Manual loaded: {len(_vectorstore)} chunks for keyword search", flush=True)
    return _vectorstore

# -----------------------------------------------------------------------
# Tool 1: DB Analytics (calls Spring REST API)
# -----------------------------------------------------------------------
def db_analysis_tool(query: str) -> str:
    """공장 AI 비전 시스템의 실시간 검출 데이터를 DB에서 조회합니다."""
    try:
        # 최근 50개 로그 가져오기 (인증 없이 접근 가능한 내부 API)
        # Spring Security에서 /api/history/recent는 인증 필요이므로
        # 로컬 호출 시 기본 admin 자격증명 사용
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/history/recent",
            auth=("admin", "admin123"),
            timeout=5
        )
        if resp.status_code != 200:
            return f"DB 조회 실패 (HTTP {resp.status_code}). Spring Boot 서버가 실행 중인지 확인하세요."

        logs = resp.json()
        if not logs:
            return "현재 검출 이력이 없습니다. AI 카메라를 통해 검사가 진행되면 데이터가 쌓입니다."

        total = len(logs)
        defects = sum(1 for l in logs if l.get("defect", False))
        normal = total - defects
        defect_rate = (defects / total * 100) if total > 0 else 0
        yield_rate = 100 - defect_rate

        # 카메라별 집계
        cam_stats = {}
        for l in logs:
            cam = l.get("cameraId", "unknown")
            cam_stats[cam] = cam_stats.get(cam, {"total": 0, "defects": 0})
            cam_stats[cam]["total"] += 1
            if l.get("defect", False):
                cam_stats[cam]["defects"] += 1

        cam_summary = "\n".join([
            f"  - {cam}: 총 {v['total']}건, 불량 {v['defects']}건"
            for cam, v in sorted(cam_stats.items())
        ])

        latest_labels = [l.get("label", "unknown") for l in logs[:5]]

        return f"""📊 최근 {total}건 검출 데이터 분석 결과:
- 전체 검사: {total}건
- 정상 (OK): {normal}건
- 불량 (NG): {defects}건
- 불량률: {defect_rate:.1f}%
- 수율: {yield_rate:.1f}%

카메라별 현황:
{cam_summary}

최근 불량 레이블: {', '.join(latest_labels)}"""

    except requests.exceptions.ConnectionError:
        return "Spring Boot 서버에 연결할 수 없습니다 (localhost:9090). 서버가 실행 중인지 확인하세요."
    except Exception as e:
        return f"DB 조회 중 오류 발생: {str(e)}"


# -----------------------------------------------------------------------
# Tool 2: Manual RAG
# -----------------------------------------------------------------------
def manual_rag_tool(query: str) -> str:
    """공장 설비 매뉴얼(SOP)에서 유지보수 정보를 검색합니다."""
    try:
        chunks = get_manual_chunks()
        
        # 간단한 키워드 추출 (조사 제거)
        keywords = [k for k in query.replace("?", "").replace(".", "").split() if len(k) > 1]
        if not keywords:
            keywords = [query]
            
        # 키워드 매칭 점수 계산
        scored_chunks = []
        for chunk in chunks:
            score = sum(1 for kw in keywords if kw.lower() in chunk.lower())
            if score > 0:
                scored_chunks.append((score, chunk))
                
        scored_chunks.sort(key=lambda x: x[0], reverse=True)
        top_docs = [c[1] for c in scored_chunks[:3]]
        
        if not top_docs:
            return "관련 매뉴얼 내용을 찾지 못했습니다. 현장 담당자에게 문의하세요."
            
        context = "\n\n---\n\n".join(top_docs)
        return f"📖 매뉴얼 검색 결과:\n\n{context}"
    except Exception as e:
        return f"매뉴얼 검색 중 오류: {str(e)}"


# -----------------------------------------------------------------------
# Build LangChain Agent
# -----------------------------------------------------------------------
def build_agent():
    global _agent_executor
    if _agent_executor is not None:
        return _agent_executor

    if not GEMINI_API_KEY:
        print("[Copilot] ERROR: GEMINI_API_KEY not set!", flush=True)
        return None

    print("[Copilot] Building LangChain ReAct Agent...", flush=True)

    llm = ChatGoogleGenerativeAI(
        model="gemini-2.5-flash",
        google_api_key=GEMINI_API_KEY,
        temperature=0.1,
        convert_system_message_to_human=True
    )

    tools = [
        Tool(
            name="DB분석도구",
            func=db_analysis_tool,
            description=(
                "공장 AI 비전 시스템의 실제 검출 데이터를 DB에서 조회합니다. "
                "사용 상황: 불량 건수, 수율, 카메라별 통계, 오늘 검사 결과 등 "
                "수치 데이터를 물어볼 때 사용하세요. "
                "예: '오늘 불량이 몇 건이야?', 'CAM-01 수율 알려줘', '최근 검출 결과'"
            )
        ),
        Tool(
            name="매뉴얼검색도구",
            func=manual_rag_tool,
            description=(
                "공장 설비 매뉴얼(SOP)에서 유지보수, 오류 조치, 장비 운용 방법을 검색합니다. "
                "사용 상황: 장비 오류, 오류 코드, 정비 방법, 절차를 물어볼 때 사용하세요. "
                "예: 'Error Code 404 어떻게 고쳐?', 'E-Stop 해제 방법', 'Reject Kicker 오류'"
            )
        )
    ]

    # Tool Calling 프롬프트
    system_prompt = """당신은 SmartFactory Vision의 AI 비서 '코파일럿(Copilot)'입니다.
    당신은 스마트팩토리의 AI 비전 검사 시스템을 관리하는 전문 AI입니다.

    다음 두 가지 도구를 적절히 사용하여 사용자의 질문에 답하세요:
    1. DB분석도구: 실시간 검출 데이터, 불량 통계, 수율 등 수치 조회 시 사용
    2. 매뉴얼검색도구: 장비 오류 조치, 유지보수 방법, SOP 절차 조회 시 사용

    답변 규칙:
    - 항상 한국어로 답변하세요.
    - 간결하고 실용적인 답변을 제공하세요.
    - 데이터 기반 답변 시 수치를 명확히 표시하세요.
    - 안전과 관련된 내용은 주의 사항을 강조하세요."""

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", "{input}"),
        ("placeholder", "{agent_scratchpad}"),
    ])

    agent = create_tool_calling_agent(llm, tools, prompt)
    _agent_executor = AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        max_iterations=5,
        handle_parsing_errors=True,
        return_intermediate_steps=False
    )

    print("[Copilot] Agent ready!", flush=True)
    return _agent_executor


# -----------------------------------------------------------------------
# API Endpoints
# -----------------------------------------------------------------------
@app.on_event("startup")
async def startup_event():
    """서버 시작 시 FAISS 벡터스토어 미리 빌드"""
    print("[Copilot] Server starting up...", flush=True)
    if GEMINI_API_KEY:
        try:
            get_manual_chunks()
            build_agent()
        except Exception as e:
            print(f"[Copilot] Startup init failed (will retry on first request): {e}", flush=True)
    else:
        print("[Copilot] WARNING: GEMINI_API_KEY not set. Set it in .env file.", flush=True)


@app.get("/health")
def health():
    return {
        "status": "UP",
        "gemini_key_loaded": bool(GEMINI_API_KEY),
        "manual_exists": MANUAL_PATH.exists(),
        "agent_ready": _agent_executor is not None
    }


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    """메인 챗봇 엔드포인트"""
    print(f"[Copilot] Question: {req.question[:80]}...", flush=True)

    if not GEMINI_API_KEY:
        return ChatResponse(
            answer="⚠️ GEMINI_API_KEY가 설정되지 않았습니다. .env 파일에 키를 입력해주세요.",
            error="API_KEY_MISSING"
        )

    agent = build_agent()
    if agent is None:
        return ChatResponse(
            answer="AI Copilot 초기화에 실패했습니다. 서버 로그를 확인해주세요.",
            error="AGENT_INIT_FAILED"
        )

    try:
        result = agent.invoke({"input": req.question})
        answer = result.get("output", "답변을 생성할 수 없습니다.")
        return ChatResponse(answer=answer, tool_used="agent")

    except ResourceExhausted:
        print("[Copilot] Rate limit hit (429)", flush=True)
        return ChatResponse(
            answer="⚠️ 죄송합니다. 현재 AI API 호출 할당량이 초과되었습니다. 무료 티어의 분당 요청 제한에 도달했을 수 있으니, 잠시 후 다시 질문해 주세요.",
            error="API_RATE_LIMIT"
        )
    except Exception as e:
        error_msg = str(e)
        print(f"[Copilot] Error: {error_msg}", flush=True)
        # 간단한 fallback: 매뉴얼 검색만 시도
        try:
            rag_result = manual_rag_tool(req.question)
            return ChatResponse(
                answer=f"(매뉴얼 검색 결과)\n\n{rag_result}",
                tool_used="rag_fallback"
            )
        except:
            return ChatResponse(
                answer=f"죄송합니다. 처리 중 오류가 발생했습니다: {error_msg[:100]}",
                error=error_msg
            )


# -----------------------------------------------------------------------
# Entry point
# -----------------------------------------------------------------------
if __name__ == "__main__":
    print(f"[Copilot] Starting server on port {PORT}...", flush=True)
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info")
