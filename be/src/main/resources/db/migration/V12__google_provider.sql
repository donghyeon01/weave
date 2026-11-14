-- wave-4 Google OAuth — users.provider CHECK에 'google' 추가
ALTER TABLE users DROP CHECK chk_users_provider;
ALTER TABLE users ADD CONSTRAINT chk_users_provider CHECK (provider IN ('kakao', 'demo', 'google'));
