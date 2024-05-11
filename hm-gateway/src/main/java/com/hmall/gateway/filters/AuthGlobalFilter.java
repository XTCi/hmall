package com.hmall.gateway.filters;

import com.hmall.common.exception.UnauthorizedException;
import com.hmall.common.utils.CollUtils;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.util.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final JwtTool jwtTool;

    private final AuthProperties authProperties;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1获取requset请求
        ServerHttpRequest requests = exchange.getRequest();
        //2.判断是否要放行
        if (isExclude(requests.getPath().toString())){
            //放行
            return chain.filter(exchange);
        }
        //3.获取token
        List<String> headers = requests.getHeaders().get("authorization");
        String token = null;
        if (!CollUtils.isEmpty(headers)){
            token = headers.get(0);
        }
        Long userId = null;
        //4.校验并解析token
        try {
            userId = jwtTool.parseToken(token);
        } catch (UnauthorizedException e){
            //拦截，设置状态吗401
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        //5.传递用户信息
        String userInfo = userId.toString();
        ServerWebExchange swe = exchange.mutate()
                .request(builder -> builder.header("user-info",userInfo))
                .build();
        //放行
        return chain.filter(swe);
    }

    private boolean isExclude(String path) {
        for (String pathpattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathpattern,path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
