package com.acessorinvestimentos.recomendacoes.controller;

import com.acessorinvestimentos.recomendacoes.dto.RecommendationResponseDto;
import com.acessorinvestimentos.recomendacoes.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/{userId}/generate")
    @PreAuthorize("#userId == authentication.name")
    public List<RecommendationResponseDto> generateRecommendations(
            @PathVariable String userId, @AuthenticationPrincipal Jwt jwt) {
        return recommendationService.generateRecommendations(jwt.getSubject());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("#userId == authentication.name")
    public List<RecommendationResponseDto> getRecommendationsByUserId(
            @PathVariable String userId, @AuthenticationPrincipal Jwt jwt) {
        return recommendationService.getRecommendationsByUserId(jwt.getSubject());
    }
}
