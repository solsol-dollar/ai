package com.shinhan.eclipse.ai.batch.translation;

import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class NewsTranslationProcessor implements ItemProcessor<IpoNews, TranslationItem> {

    private final ChatModel chatModel;

    @Value("${app.embedding.min-summary-length:50}")
    private int minSummaryLength;

    private static final String SYSTEM_PROMPT = """
            당신은 금융·경제 뉴스 한국어 요약 전문가입니다.
            제공된 IPO 뉴스를 아래 형식으로만 출력하세요.

            제목번역: <자연스러운 한국어 제목, 회사명·티커는 영문 유지>
            요약: <아래 항목 중 본문에서 확인되는 내용을 중심으로 3~5문장 작성>

            요약에 포함할 항목 (확인 가능한 것만):
            1. 기업 소개 — 사업 영역, 주요 제품·서비스, 모회사나 배경이 있다면 함께 기술
            2. IPO 구조 — 공모 규모, 공모가(또는 희망 범위), 발행 주식 수, 상장 거래소·티커
            3. 기업 가치 및 수요 — 밸류에이션, 청약 경쟁률, 기관 투자자 참여 현황
            4. 시장 맥락·리스크 — 공모가 결정 배경, 시장 변동성, 경쟁 환경, 우려 요인
            5. 자금 사용처 또는 향후 계획 — 조달 자금 활용 방안, 성장 전략, 주요 일정

            문장 흐름 가이드:
            - 첫 문장: 기업이 무엇을 하는 회사인지 + 이번 IPO의 핵심 사실
            - 중간 문장: 딜 구조와 시장 반응을 수치의 의미와 함께 기술
            - 마지막 문장: 리스크 또는 향후 전망으로 마무리

            규칙:
            - 한국경제·매일경제 보도체로 작성할 것
            - 번역투 금지, 자연스러운 한국어 문장으로 작성할 것
            - 회사명·티커·거래소명은 영문 원문 그대로 유지할 것
            - 금액·주식 수는 숫자로 표현할 것 (예: $18, $134억, 5,500만 주)
            - 수치는 단순 나열 금지, 반드시 의미·맥락과 함께 기술할 것
              (예: "5배 이상의 청약 경쟁률을 기록했음에도 가격을 하단으로 낮췄다")
            - 주관사·인수단 명단은 딜 규모가 특별히 클 때만 언급할 것
            - 본문에 없는 정보는 절대 추가하지 말 것
            - 투자 권유 표현 금지
            """;

    @Override
    public TranslationItem process(IpoNews news) {
        try {
            String body = news.getContent() != null && news.getContent().length() >= minSummaryLength
                    ? news.getContent().substring(0, Math.min(2000, news.getContent().length()))
                    : (news.getSummary() != null ? news.getSummary() : "");

            String userPrompt = "제목: " + news.getTitle() + "\n본문: " + body;

            String raw = chatModel.call(
                    new Prompt(List.of(
                            new SystemMessage(SYSTEM_PROMPT),
                            new UserMessage(userPrompt)))
            ).getResult().getOutput().getText().trim();

            String titleKo = extractLine(raw, "제목번역:");
            String summaryKo = extractLine(raw, "요약:");

            // 길이 제한
            if (titleKo != null && titleKo.length() > 250) titleKo = titleKo.substring(0, 250);
            if (summaryKo != null && summaryKo.length() > 2000) summaryKo = summaryKo.substring(0, 2000);

            log.info("번역 완료: newsId={}, titleKo={}", news.getId(), titleKo);
            return TranslationItem.of(news, titleKo, summaryKo);

        } catch (Exception e) {
            log.warn("번역 실패: newsId={}, error={}", news.getId(), e.getMessage());
            return TranslationItem.of(news, null, null);
        }
    }

    private String extractLine(String raw, String prefix) {
        for (String line : raw.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
