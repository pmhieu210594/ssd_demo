# AI Review - AI-QUALITY

## 1) Tom tat diff (5 dong)
- BE them moi full slice AI-QUALITY: migration, model, repository port/adapter/mapper, service, controller, DTO, unit test/controller test.
- FE them trang `ai-quality`, route guard, API endpoints, page/components, va test Vitest cho API + UI.
- RBAC duoc trien khai theo tier `MUTATE/VIEW_ONLY/NONE`; co endpoint `GET /api/v1/ai-qualities/access` cho guard.
- Co thay doi BE ngoai pham vi ticket o `SddPlatformApplication.java` va `application.yml` (config Flyway/datasource/CORS).
- Git diff tracked cua FE hien chu yeu o tai lieu/test file khac ticket; phan AI-QUALITY o FE/BE dang nam nhieu o file moi.

## 2) Findings

### F1 - Blocker - AC-4 chua dat (ticket-repository parentage)
- Loai: Correctness
- Mo ta: Spec yeu cau `ticket_id` phai thuoc `repository_id` (AC-AI-QUALITY-4/BR-3), nhung code hien chi validate `ticket` thuoc `project`.
- Evidence:
  - [AiQualityService.java](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/aiquality/AiQualityService.java:264) comment neu ro khong check ticket->repository, chuyen thanh ticket->project.
  - [AiQualityService.java](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/aiquality/AiQualityService.java:274) `ensureTicketBelongsToProject(...)` dung `ticketLookup.existsTicketInProject(...)`.
  - [AiQualityServiceTest.java](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/aiquality/AiQualityServiceTest.java:240) test hien cung chi assert "ticket not belonging to project".
  - [spec-pack.md](/d:/DataAnalyticsProject/source/EDCAP/docs/changes/AI-QUALITY/spec-pack.md:89) BR-3 yeu cau ticket thuoc repository.
- Tac dong: Co the create ban ghi AI Quality cho ticket khong thuoc repository da chon.

### F2 - Blocker - AC-11/BR-5 lech tai access endpoint khi thieu projectId
- Loai: Security / Authorization
- Mo ta: Caller `NONE` van qua duoc `GET /access` neu khong truyen `projectId` (chi can authenticated), trai voi spec "NONE bi deny screen va API".
- Evidence:
  - [AiQualityController.java](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/java/com/sdd/platform/web/rest/AiQualityController.java:78) branch `projectId == null` goi `requireAnyAccess`.
  - [AiQualityService.java](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/aiquality/AiQualityService.java:88) `requireAnyAccess` chi check `caller != null`.
  - [AiQualityServiceTest.java](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/aiquality/AiQualityServiceTest.java:123) test xac nhan user role khong hop le van pass `requireAnyAccess`.
  - [spec-pack.md](/d:/DataAnalyticsProject/source/EDCAP/docs/changes/AI-QUALITY/spec-pack.md:95) BR-5: `NONE` denied screen + API.
- Tac dong: Non-authorized role co the vao man hinh truoc khi bi chan o call khac; vi pham contract authorization cua feature.

### F3 - Major - Regression risk do thay doi config BE ngoai pham vi ticket
- Loai: Regression risk
- Mo ta: Co thay doi default datasource va Flyway behavior trong `application.yml` khong thuoc AC AI-QUALITY.
- Evidence:
  - [application.yml](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/resources/application.yml:11) doi DB mac dinh sang `sdd_platform5`.
  - [application.yml](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_BE/src/main/resources/application.yml:24) bat `repair-on-migrate` va `out-of-order`.
- Tac dong: Tang rui ro chay sai DB/local profile va thay doi semantics migrate cho toan he thong.

### F4 - Minor - Missing test cho loi 409 hien thi inline o FE
- Loai: Missing tests
- Mo ta: Spec yeu cau duplicate ticket khi create phai surface 409 inline trong form; test FE chua assert case nay.
- Evidence:
  - [review-checklist.md](/d:/DataAnalyticsProject/source/EDCAP/docs/changes/AI-QUALITY/review-checklist.md:82) yeu cau ro 409 inline.
  - [ai-quality.test.tsx](/d:/DataAnalyticsProject/source/EDCAP/EDCAP_FE/src/__%20tests%20__/ai-quality/ai-quality.test.tsx:247) chi test happy path create.

## 3) AC chua dat / lech dac ta
- AC-AI-QUALITY-4: Chua dat. Implemented check la ticket->project, khong phai ticket->repository.
- AC-AI-QUALITY-11 (va BR-5): Chua dat tai endpoint `GET /access` khi `projectId` null (role NONE van pass).

## 4) De xuat test cases bo sung
1. BE UT/IT - Ticket/repository mismatch
- Setup: ticket thuoc cung project nhung khong thuoc repository duoc submit.
- Verify: `POST /api/v1/ai-qualities` tra 400 voi ma loi mismatch dung BR-3.

2. BE Controller test - NONE access with no projectId
- Setup: authenticated caller co role khong map (`VIEWER`/none project role).
- Verify: `GET /api/v1/ai-qualities/access` khong kem `projectId` phai tra 403 (neu bam dung BR-5).

3. FE component test - duplicate create 409 inline
- Mock `endpoints.aiQuality.create` reject `ApiError(409, ...)`.
- Verify: Drawer hien thi thong bao inline/localized theo error code, khong chi toast generic.

4. BE IT - Soft-delete recreate lifecycle with unique index
- Create -> soft delete -> create lai cung ticket.
- Verify: lan 2 thanh cong va chi co 1 active row theo partial unique index.

## 5) Ket luan
- Verdict: Request changes.
- Uu tien fix truoc merge: F1, F2.
- F3 can xac nhan chu dich voi owner ticket; neu khong chu dich thi nen loai khoi PR/tap diff release cua AI-QUALITY.

## 6) STEP tracking

### F1 - Done (2026-08-18)
- Pham vi: Chi xu ly F1 (AC-AI-QUALITY-4 / BR-3: ticket phai thuoc repository).
- File da sua:
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/usecase/aiquality/AiQualityService.java`
  - `EDCAP_BE/src/main/java/com/sdd/platform/application/port/out/persistence/TicketLookupPort.java`
  - `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/adapter/TicketLookupJdbcAdapter.java`
  - `EDCAP_BE/src/main/java/com/sdd/platform/infrastructure/persistence/mapper/TicketLookupMapper.java`
  - `EDCAP_BE/src/main/resources/mapper/TicketLookupMapper.xml`
  - `EDCAP_BE/src/test/UnitTest/java/com/sdd/platform/application/usecase/aiquality/AiQualityServiceTest.java`
- Command/evidence:
  - `mvn clean verify` (EDCAP_BE): **FAIL** do khong xoa duoc `target/test-fixtures/changes/PARSER-SPEC-PACK/spec-pack.md` (clean step).
  - `mvn test` (EDCAP_BE): **PASS** (`Tests run: 556, Failures: 0, Errors: 0, Skipped: 0`).
  - `mvn -Dtest=AiQualityServiceTest test` (EDCAP_BE): **PASS** (`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`).
- Ket qua F1:
  - `AiQualityService#create(...)` da doi tu check `ticket -> project` sang `ticket -> repository`.
  - Them lookup `existsTicketInRepository(ticketId, repositoryId)` o port/adapter/mapper/sql.
  - Unit test F1 da doi sang case `create_ticketNotBelongingToRepository_throwsBusinessRuleException`.

### F4 - Done (2026-08-18)
- Pham vi: Chi xu ly F4 (dong bo spec/checklist voi behavior hien tai), khong mo rong sang F1/F2/F3.
- Trang thai: **DONE** theo xac nhan cua user: chap nhan behavior toast hien tai cho duplicate `409`.
- Ket qua:
  - `spec-pack.md` da cap nhat mo ta duplicate `409` duoc hien thi qua localized toast/global error notification.
  - `review-checklist.md` da doi tieu chi FE tu "409 inline trong form" sang "409 qua localized toast/global error notification".
- File da sua:
  - `docs/changes/AI-QUALITY/spec-pack.md`
  - `docs/changes/AI-QUALITY/review-checklist.md`
  - `docs/changes/AI-QUALITY/ai-review.md`
- Command/evidence:
  - `npm run test:unit -- --run src/__\ tests\ __/ai-quality/ai-quality.test.tsx` (EDCAP_FE): **PASS** (`1 passed`, `6 tests passed`).
  - `npm run lint` (EDCAP_FE): **PASS** (exit code 0, khong co lint error output).
