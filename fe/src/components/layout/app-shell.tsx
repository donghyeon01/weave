import { useEffect } from "react";
import { Navigate, useOutlet } from "react-router-dom";
import { startStompClient, stopStompClient } from "../../lib/stomp";
import { registerNotificationPush } from "../../features/notifications/stores/notification-push";
import { useAuthStore } from "../../stores/auth-store";
import { ContentHeader } from "./content-header";
import { HeaderActionsProvider } from "./header-actions-provider";
import { Sidebar } from "./sidebar";
import { TopNav } from "./top-nav";

// /app 보호 셸 — status가 auth-store의 단일 원천.
// 셸 분기(SYSTEM.md 앱 셸 분기): 데스크톱=사이드바+콘텐츠 헤더, 모바일(<768px)=TopNav.
export function AppShell() {
  const status = useAuthStore((s) => s.status);
  const outlet = useOutlet();

  // STOMP는 authed 동안만 유지 — logout·탈퇴 시 해제해 좀비 연결을 막는다
  useEffect(() => {
    if (status === "authed") {
      registerNotificationPush();
      startStompClient();
      return () => stopStompClient();
    }
  }, [status]);

  if (status === "booting") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-sm font-semibold text-text-secondary">
          세션을 복원하고 있어요…
        </p>
      </div>
    );
  }
  if (status === "guest") return <Navigate to="/" replace />;

  return (
    <HeaderActionsProvider>
      <div className="min-h-screen bg-background text-text">
        <TopNav />
        <div className="flex">
          <Sidebar />
          <div className="flex min-w-0 flex-1 flex-col">
            <ContentHeader />
            <main className="mx-auto w-full max-w-280 px-4 py-8 sm:px-8">
              {outlet ?? (
                // 셸의 기본 빈 상태 — 자식 라우트가 아직 없는 동안만 표시된다
                <div className="rounded-2xl border-2 border-dashed border-border bg-surface p-16 text-center">
                  <h1 className="font-display text-2xl">대시보드</h1>
                  <p className="mt-3 text-sm text-text-secondary">
                    로그인이 완료됐어요. 각 화면은 다음 마일스톤에서 순차적으로
                    추가됩니다.
                  </p>
                </div>
              )}
            </main>
          </div>
        </div>
      </div>
    </HeaderActionsProvider>
  );
}
