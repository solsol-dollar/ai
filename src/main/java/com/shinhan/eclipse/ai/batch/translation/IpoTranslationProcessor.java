package com.shinhan.eclipse.ai.batch.translation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScore;
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
public class IpoTranslationProcessor implements ItemProcessor<IpoScore, IpoTranslationItem> {

    private final ChatModel chatModel;
    private final IpoNewsRepository newsRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.embedding.min-summary-length:50}")
    private int minBodyLength;

    private static final String SYSTEM_PROMPT = """
            당신은 금융·경제 뉴스 한국어 요약 전문가입니다.
            아래 두 개의 IPO 관련 뉴스를 종합하여 투자자에게 유용한 통합 요약을 작성하세요.

            출력 형식 (이 형식 외 다른 텍스트 출력 금지):
            제목1: <뉴스1 제목의 자연스러운 한국어 번역>
            제목2: <뉴스2 제목의 자연스러운 한국어 번역>
            요약: <두 기사를 종합한 3~5문장 통합 요약>

            요약에 포함할 항목 (본문에서 확인되는 것만):
            1. 기업 소개 — 사업 영역, 주요 제품·서비스, 모회사나 배경
            2. IPO 구조 — 공모 규모, 공모가(또는 희망 범위), 주식 수, 상장 거래소·티커
            3. 기업 가치 및 수요 — 밸류에이션, 청약 경쟁률, 주요 투자자 참여
            4. 시장 맥락·리스크 — 가격 결정 배경, 시장 변동성, 우려 요인
            5. 자금 사용처 또는 향후 계획

            문장 흐름:
            - 첫 문장: 기업이 무엇을 하는 회사인지 + 이번 IPO의 핵심 사실
            - 중간 문장: 딜 구조와 시장 반응을 수치의 의미와 함께 기술
            - 마지막 문장: 리스크 또는 향후 전망으로 마무리

            규칙:
            - 두 기사의 중복 내용은 한 번만 기술하고 보완 정보는 자연스럽게 통합할 것
            - 한국경제·매일경제 보도체로 작성할 것
            - 번역투 금지, 자연스러운 한국어 문장으로 작성할 것
            - 회사명·티커·거래소명은 영문 원문 그대로 유지할 것
            - 금액·주식 수는 숫자로 표현할 것 (예: $18, $134억, 5,500만 주)
            - 수치는 단순 나열 금지, 반드시 의미·맥락과 함께 기술할 것
            - 본문에 없는 정보는 절대 추가하지 말 것
            - 투자 권유 표현 금지
            """;

    @Override
    public IpoTranslationItem process(IpoScore score) {
        List<Long> ids = parseIds(score.getTopNewsIds());
        if (ids.isEmpty()) return null;

        IpoNews news1 = ids.size() > 0 ? newsRepository.findById(ids.get(0)).orElse(null) : null;
        IpoNews news2 = ids.size() > 1 ? newsRepository.findById(ids.get(1)).orElse(null) : null;

        if (news1 == null) return null;

        String userPrompt = buildPrompt(news1, news2);
        try {
            String raw = chatModel.call(
                    new Prompt(List.of(
                            new SystemMessage(SYSTEM_PROMPT),
                            new UserMessage(userPrompt)))
            ).getResult().getOutput().getText().trim();

            String titleKo1 = extractLine(raw, "제목1:");
            String titleKo2 = extractLine(raw, "제목2:");
            String summary  = extractLine(raw, "요약:");

            if (titleKo1 != null && titleKo1.length() > 250) titleKo1 = titleKo1.substring(0, 250);
            if (titleKo2 != null && titleKo2.length() > 250) titleKo2 = titleKo2.substring(0, 250);
            if (summary  != null && summary.length()  > 2000) summary  = summary.substring(0, 2000);

            log.info("통합 요약 완료: ipoId={}, ticker={}", score.getIpoId(), score.getTicker());
            return new IpoTranslationItem(
                    score.getIpoId(),
                    news1.getId(), titleKo1,
                    news2 != null ? news2.getId() : null, titleKo2,
                    summary
            );
        } catch (Exception e) {
            log.warn("통합 요약 실패: ipoId={}, error={}", score.getIpoId(), e.getMessage());
            return null;
        }
    }

    private String buildPrompt(IpoNews news1, IpoNews news2) {
        StringBuilder sb = new StringBuilder();
        sb.append("[뉴스1]\n");
        sb.append("제목: ").append(news1.getTitle()).append("\n");
        sb.append("본문: ").append(body(news1)).append("\n\n");
        if (news2 != null) {
            sb.append("[뉴스2]\n");
            sb.append("제목: ").append(news2.getTitle()).append("\n");
            sb.append("본문: ").append(body(news2));
        }
        return sb.toString();
    }

    private String body(IpoNews news) {
        String content = news.getContent();
        if (content != null && content.length() >= minBodyLength)
            return content.substring(0, Math.min(2000, content.length()));
        return news.getSummary() != null ? news.getSummary() : "";
    }

    private String extractLine(String raw, String prefix) {
        for (String line : raw.split("\n")) {
            if (line.startsWith(prefix))
                return line.substring(prefix.length()).trim();
        }
        return null;
    }

    private List<Long> parseIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
