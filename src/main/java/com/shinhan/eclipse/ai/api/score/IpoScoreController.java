package com.shinhan.eclipse.ai.api.score;

import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ipo")
@RequiredArgsConstructor
public class IpoScoreController {

    private final IpoScoreRepository ipoScoreRepository;

    @GetMapping("/{ipoId}/score")
    public ResponseEntity<IpoScoreResponse> getScore(@PathVariable Long ipoId) {
        return ipoScoreRepository.findByIpoId(ipoId)
                .map(IpoScoreResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
