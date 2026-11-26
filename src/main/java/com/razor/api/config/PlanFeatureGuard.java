package com.razor.api.config;

import com.razor.api.service.PlanFeatures;
import com.razor.api.service.PlanFeatures.Feature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Barra as rotas do painel de funções que o plano da barbearia não libera (clube, pacotes): 403
 * com uma mensagem clara, em vez de deixar o dono criar algo que o plano não usa.
 */
@Configuration
@RequiredArgsConstructor
public class PlanFeatureGuard implements WebMvcConfigurer {

    private final PlanFeatures planFeatures;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard(Feature.CLUB)).addPathPatterns("/api/admin/plans", "/api/admin/plans/**");
        registry.addInterceptor(guard(Feature.PACKAGES)).addPathPatterns("/api/admin/packages", "/api/admin/packages/**");
    }

    private HandlerInterceptor guard(Feature feature) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if (planFeatures.enabled(feature)) return true;
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"Esta função não faz parte do seu plano (piloto).\"}");
                return false;
            }
        };
    }
}
