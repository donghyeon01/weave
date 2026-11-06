package app.weave.user.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getMe(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    @Transactional
    public User updateNickname(Long userId, String nickname) {
        User user = getMe(userId);
        user.changeNickname(nickname);
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

    @Transactional
    public void deleteMe(Long userId) {
        User user = getMe(userId);
        userRepository.delete(user); // FK ON DELETE CASCADE가 전 데이터 정리
    }
}
