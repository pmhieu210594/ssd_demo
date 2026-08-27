package com.sdd.platform.application.usecase.governance;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.port.out.persistence.RepositoryRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.RepositoryModel;


@Service
public class RepositoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RepositoryRepositoryPort repository;
    private final ProjectRepositoryPort projectRepository;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 12 bytes là độ dài tiêu chuẩn cho GCM
    private static final int GCM_TAG_LENGTH = 128; // Độ dài thẻ xác thực (bits): 128, 120, 112, 104, 96

    @Value("${SECRET_KEY_STRING}")
    private String SECRET_KEY_STRING;

    public RepositoryService(RepositoryRepositoryPort repository, ProjectRepositoryPort projectRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<RepositoryModel> search(UUID projectId, String status, String keyword, int page, int size, AppUser caller) {
        requireAdmin(caller);
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = trimToNull(keyword);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        int offset = normalizedPage * normalizedSize;
        List<RepositoryModel> items = repository.findPage(projectId, normalizedStatus, normalizedKeyword, offset, normalizedSize);
        for (RepositoryModel repositoryModel : items) {
            if (repositoryModel.getRepoUrlHash() != null 
                && !repositoryModel.getRepoUrlHash().isEmpty()) {
                String encryptedUrl = repositoryModel.getRepoUrlHash();
                String decryptedUrl = decrypt(encryptedUrl);
                repositoryModel.setRepoUrlHash(decryptedUrl);
            }
        }
        long total = repository.count(projectId, normalizedStatus, normalizedKeyword);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public RepositoryModel get(UUID repositoryId, AppUser caller) {
        requireAdmin(caller);
        RepositoryModel item = repository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Pages.Repository.NotFound"));
        String encryptedUrl = item.getRepoUrlHash();
        String decryptedUrl = decrypt(encryptedUrl);
        item.setRepoUrlHash(decryptedUrl);
        return item;
    }

    @Transactional
    public RepositoryModel create(UUID projectId, String repoNameMasked, String hostType, String defaultBranch, String repoUrlHash, AppUser caller) {
        requireAdmin(caller);
        RepositoryPayload payload = normalize(projectId, repoNameMasked, hostType, defaultBranch, repoUrlHash);
        ensureProjectExists(payload.projectId());
        ensureUniqueName(payload.projectId(), payload.repoNameMasked(), null);
        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        RepositoryModel entity = RepositoryModel.builder()
                .repositoryId(UUID.randomUUID())
                .projectId(payload.projectId())
                .projectAlias(null)
                .repoNameMasked(payload.repoNameMasked())
                .hostType(RepositoryModel.HostType.valueOf(payload.hostType()))
                .defaultBranch(payload.defaultBranch())
                .repoUrlHash(encrypt(payload.repoUrlHash()))
                .status(RepositoryModel.RepositoryStatus.ACTIVE)
                .deleteFlag(false)
                .createdAt(now)
                .createdBy(actor)
                .updatedAt(now)
                .updatedBy(actor)
                .build();
        repository.insert(entity);
        return get(entity.getRepositoryId(), caller);
    }

    @Transactional
    public RepositoryModel update(UUID repositoryId, UUID projectId, String repoNameMasked, 
        String hostType, String defaultBranch, String repoUrlHash, AppUser caller) {
        requireAdmin(caller);
        RepositoryModel existing = repository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Pages.Repository.NotFound"));
        if (existing.isDeleted()) {
            throw new NotFoundException("Pages.Repository.NotFound");
        }
        RepositoryPayload payload = normalize(projectId, repoNameMasked, hostType, defaultBranch, repoUrlHash);
        ensureProjectExists(payload.projectId());
        ensureUniqueName(payload.projectId(), payload.repoNameMasked(), repositoryId);
        String actor = resolveActor(caller);
        existing.setProjectId(payload.projectId());
        existing.setRepoNameMasked(payload.repoNameMasked());
        existing.setHostType(RepositoryModel.HostType.valueOf(payload.hostType()));
        existing.setDefaultBranch(payload.defaultBranch());
        existing.setRepoUrlHash(encrypt(payload.repoUrlHash()));
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(actor);
        int affected = repository.update(existing);
        if (affected == 0) {
            throw new NotFoundException("Pages.Repository.NotFound");
        }
        return get(repositoryId, caller);
    }

    @Transactional
    public RepositoryModel softDelete(UUID repositoryId, AppUser caller) {
        requireAdmin(caller);
        RepositoryModel existing = repository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Pages.Repository.NotFound"));
        if (existing.isDeleted()) {
            throw new NotFoundException("Pages.Repository.NotFound");
        }
        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        int affected = repository.softDelete(repositoryId, actor, now, actor, now);
        if (affected == 0) {
            throw new NotFoundException("Pages.Repository.NotFound");
        }
        return repository.findById(repositoryId).orElseThrow(() -> new NotFoundException("Pages.Repository.NotFound"));
    }

    private void ensureProjectExists(UUID projectId) {
        if (projectId == null) {
            throw new BusinessRuleException("Pages.Repository.Project.Required");
        }
        if (!projectRepository.findById(projectId).isPresent()) {
            throw new NotFoundException("Pages.Repository.Project.NotFound");
        }
    }

    private void ensureUniqueName(UUID projectId, String repoNameMasked, UUID excludeRepositoryId) {
        if (repository.existsActiveName(projectId, repoNameMasked, excludeRepositoryId)) {
            throw new BusinessRuleException("Pages.Repository.Name.Duplicate");
        }
    }

    private RepositoryPayload normalize(UUID projectId, String repoNameMasked, String hostType, String defaultBranch, String repoUrlHash) {
        UUID normalizedProjectId = projectId;
        String normalizedName = trimToNull(repoNameMasked);
        if (normalizedProjectId == null) {
            throw new BusinessRuleException("Pages.Repository.Project.Required");
        }
        if (normalizedName == null) {
            throw new BusinessRuleException("Pages.Repository.Name.Required");
        }
        if (normalizedName.length() > 255) {
            throw new BusinessRuleException("Pages.Repository.Name.MaxLength");
        }
        String normalizedHostType = trimToNull(hostType);
        if (normalizedHostType == null) {
            throw new BusinessRuleException("Pages.Repository.HostType.Required");
        }
        normalizedHostType = normalizedHostType.toUpperCase(Locale.ROOT);
        try {
            RepositoryModel.HostType.valueOf(normalizedHostType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Pages.Repository.HostType.Invalid");
        }
        String normalizedBranch = trimToNull(defaultBranch);
        if (normalizedBranch != null && normalizedBranch.length() > 100) {
            throw new BusinessRuleException("Pages.Repository.DefaultBranch.MaxLength");
        }
        String normalizedUrl = trimToNull(repoUrlHash);
        if (normalizedUrl != null && normalizedUrl.length() > 2048) {
            throw new BusinessRuleException("Pages.Repository.Url.MaxLength");
        }
        return new RepositoryPayload(normalizedProjectId, normalizedName, normalizedHostType, normalizedBranch, normalizedUrl);
    }

    /**
     * Hàm mã hóa dữ liệu văn bản (Plaintext) thành chuỗi mã hóa (Base64)
     */
    public String encrypt(String plaintext) {
        try {
            if (plaintext == null || plaintext.isEmpty()) return "";
            String base64Key = SECRET_KEY_STRING; 

            if (base64Key == null || base64Key.isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy SECRET_KEY_STRING trong file .env");
            }
    
            // Chuyển chuỗi đó thành đối tượng SecretKey để dùng cho hàm encrypt/decrypt
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);
            SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            // 1. Tạo IV ngẫu nhiên bảo mật
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // 2. Khởi tạo Cipher cấu hình GCM và chế độ Mã hóa
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            // 3. Tiến hành mã hóa dữ liệu gốc
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // 4. Gộp IV và Ciphertext vào một mảng byte duy nhất để dễ lưu trữ/truyền tải
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            
            // 5. Chuyển kết quả mảng byte thành chuỗi Base64 để hiển thị/truyền đi dưới dạng text
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình giải mã:", e);
        }
    }

    /**
     * Hàm giải mã chuỗi mã hóa (Base64) trở lại thành văn bản gốc
     */
    public String decrypt(String encryptedBase64) {
        try {
            if (encryptedBase64 == null || encryptedBase64.isEmpty()) return "";
            String base64Key = SECRET_KEY_STRING; 
            if (base64Key == null || base64Key.isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy SECRET_KEY_STRING trong file .env");
            }
            // Chuyển chuỗi đó thành đối tượng SecretKey để dùng cho hàm encrypt/decrypt
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);
            SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
            // 1. Chuyển chuỗi mã hóa mã Base64 ngược lại thành mảng byte
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            
            // 2. Tách lấy IV từ đầu mảng byte dữ liệu kết hợp
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            
            // 3. Tách phần Ciphertext còn lại ở phía sau
            int ciphertextLength = combined.length - GCM_IV_LENGTH;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertextLength);
            
            // 4. Khởi tạo Cipher với IV đã tách và cấu hình ở chế độ Giải mã
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            // 5. Thực hiện giải mã và chuyển kết quả thành String UTF-8
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình mã hóa:", e);
        }
        
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private String resolveActor(AppUser caller) {
        if (caller == null) {
            return "SYSTEM";
        }
        if (caller.getEmail() != null && !caller.getEmail().isBlank()) {
            return caller.getEmail().trim();
        }
        if (caller.getDisplayName() != null && !caller.getDisplayName().isBlank()) {
            return caller.getDisplayName().trim();
        }
        return "SYSTEM";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record RepositoryPayload(UUID projectId, String repoNameMasked, String hostType, String defaultBranch, String repoUrlHash) {}

    private String normalizeStatusFilter(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(normalized) && !"ACTIVE".equals(normalized) && !"DELETED".equals(normalized)) {
            throw new BusinessRuleException("Pages.Project.Status.Invalid");
        }
        return normalized;
    }
}
