package com.example.moneymind.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

internal data class PrivacyNoticeSection(
    val title: String,
    val body: String
)

internal const val privacyPolicyUrl =
    "https://github.com/Sialll/NEW-PROJECT/blob/main/docs/privacy-policy.md"

internal val privacyNoticeSections = listOf(
    PrivacyNoticeSection(
        title = "수집 항목",
        body = "거래 알림 본문, 사용자가 직접 선택한 명세서 파일, 수동 입력 거래, 예산/룰/계좌 설정을 저장합니다."
    ),
    PrivacyNoticeSection(
        title = "이용 목적",
        body = "가계부 기록, 자동 분류, 예산 진행도 계산, CSV 내보내기와 월말 정산에만 사용합니다."
    ),
    PrivacyNoticeSection(
        title = "외부 전송",
        body = "기본 앱 구성에는 광고, 분석, 외부 동기화 서버가 없으며 데이터는 기기 내부에만 유지됩니다."
    ),
    PrivacyNoticeSection(
        title = "사용자 제어",
        body = "옵션 화면에서 CSV 내보내기, 기간 초기화, 전체 기록 초기화, 공장초기화를 직접 실행할 수 있습니다."
    )
)

internal fun openPrivacyPolicy(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
