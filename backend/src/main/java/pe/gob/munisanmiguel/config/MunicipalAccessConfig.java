package pe.gob.munisanmiguel.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import pe.gob.munisanmiguel.service.MunicipalAccessService;

@Configuration
public class MunicipalAccessConfig implements WebMvcConfigurer {
    private final MunicipalAccessService access;
    public MunicipalAccessConfig(MunicipalAccessService access){this.access=access;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
        @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,Object handler){String uri=request.getRequestURI().substring(request.getContextPath().length());access.requirePathAccess(uri,access.current());return true;}
    }).addPathPatterns("/api/expedientes/**","/api/programaciones/**","/api/revisiones/**","/api/actas/**");}
}
