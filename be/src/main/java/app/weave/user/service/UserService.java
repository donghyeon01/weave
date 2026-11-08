package app.weave.user.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.UserFileCleanup;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserFileCleanup fileCleanup;

    public UserService(UserRepository userRepository, UserFileCleanup fileCleanup) {
        this.userRepository = userRepository;
        this.fileCleanup = fileCleanup;
    }

    @Transactional(readOnly = true)
    public User getMe(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    /** 프로필·설정 부분 수정 — null 인자는 변경하지 않는다 (계약 §3.2). */
    @Transactional
    public User updateMe(Long userId, String nickname, String bio, User.Theme theme,
            Boolean shareBusyOnly, Boolean shareTitles, Boolean shareDetails) {
        User user = getMe(userId);
        user.updateProfile(nickname, bio, theme, shareBusyOnly, shareTitles, shareDetails);
        return user;
    }

    @Transactional(readOnly = true)
    public User searchByEmail(String email, Long requesterId) {
        return userRepository.findByEmailAndIdNot(email, requesterId).orElse(null);
    }

    /** friend 모듈이 소비하는 공개 API — 이메일 없으면 USER-NOT-FOUND. */
    @Transactional(readOnly = true)
    public User findByEmailStrict(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 친구 목록 등 상대방 표시 정보의 배치 조회 — N+1 방지용. */
    @Transactional(readOnly = true)
    public Map<Long, User> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    @Transactional
    public void deleteMe(Long userId) {
        User user = getMe(userId);
        userRepository.delete(user); // FK ON DELETE CASCADE가 전 데이터 정리
        // 디스크 파일은 CASCADE가 못 지우므로 포트로 정리 — 실패해도 탈퇴는 완료시킨다
        try {
            fileCleanup.deleteAllForUser(userId);
        } catch (RuntimeException e) {
            log.warn("탈퇴 사용자 파일 정리 실패 userId={}", userId, e);
        }
    }
}
