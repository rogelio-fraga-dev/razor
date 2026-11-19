package com.razor.api.controller;

import com.razor.api.service.OnboardingProgressService;
import com.razor.api.service.OnboardingProgressService.BotTest;
import com.razor.api.service.OnboardingProgressService.Progress;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Assistente guiado do painel (V49): progresso por passo, conclusao e o teste do bot ao vivo.
 * Sob {@code /api/admin/**} sem regra propria no SecurityConfig, entao fica com o dono — quem
 * configura a barbearia.
 */
@RestController
@RequestMapping("/api/admin/onboarding")
@RequiredArgsConstructor
public class AdminOnboardingController {

    private final OnboardingProgressService progressService;

    @GetMapping
    public Progress get() {
        return progressService.get();
    }

    @PutMapping("/steps/{step}")
    public Progress markStep(@PathVariable String step) {
        return progressService.markStep(step);
    }

    @PostMapping("/complete")
    public Progress complete() {
        return progressService.complete();
    }

    @GetMapping("/bot-test")
    public BotTest botTest() {
        return progressService.botTest();
    }
}
