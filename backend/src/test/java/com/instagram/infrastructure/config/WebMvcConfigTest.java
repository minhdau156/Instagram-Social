package com.instagram.infrastructure.config;

import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.infrastructure.interceptor.IdempotencyInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    private WebMvcConfig createConfig() {
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(idempotencyKeyJpaRepository);
        return new WebMvcConfig(interceptor);
    }

    @Test
    void cachedBodyAttr_hasExpectedConstantValue() {
        assertThat(WebMvcConfig.CACHED_BODY_ATTR).isEqualTo("_cachedRequestBody");
    }

    @Test
    void requestCachingFilter_isNotNull() {
        FilterRegistrationBean<Filter> bean = createConfig().requestCachingFilter();
        assertThat(bean).isNotNull();
        assertThat(bean.getFilter()).isNotNull();
    }

    @Test
    void requestCachingFilter_hasOrder1() {
        FilterRegistrationBean<Filter> bean = createConfig().requestCachingFilter();
        assertThat(bean.getOrder()).isEqualTo(1);
    }

    @Test
    void requestCachingFilter_registersApiUrlPattern() {
        FilterRegistrationBean<Filter> bean = createConfig().requestCachingFilter();
        assertThat(bean.getUrlPatterns()).contains("/api/*");
    }

    @Test
    void responseCachingFilter_isNotNull() {
        FilterRegistrationBean<Filter> bean = createConfig().responseCachingFilter();
        assertThat(bean).isNotNull();
        assertThat(bean.getFilter()).isNotNull();
    }

    @Test
    void responseCachingFilter_hasOrder2() {
        FilterRegistrationBean<Filter> bean = createConfig().responseCachingFilter();
        assertThat(bean.getOrder()).isEqualTo(2);
    }

    @Test
    void responseCachingFilter_registersApiUrlPattern() {
        FilterRegistrationBean<Filter> bean = createConfig().responseCachingFilter();
        assertThat(bean.getUrlPatterns()).contains("/api/*");
    }

    @Test
    void requestCachingFilter_storesCachedBodyAttributeOnRequest() throws Exception {
        FilterRegistrationBean<Filter> bean = createConfig().requestCachingFilter();
        Filter filter = bean.getFilter();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"caption\":\"hello\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        final Object[] capturedAttr = new Object[1];
        FilterChain chain = (req, res) -> capturedAttr[0] = ((jakarta.servlet.http.HttpServletRequest) req)
                .getAttribute(WebMvcConfig.CACHED_BODY_ATTR);

        filter.doFilter(request, response, chain);

        assertThat(capturedAttr[0]).isInstanceOf(byte[].class);
        assertThat(new String((byte[]) capturedAttr[0])).isEqualTo("{\"caption\":\"hello\"}");
    }

    @Test
    void responseCachingFilter_wrapsHttpServletResponse() throws Exception {
        FilterRegistrationBean<Filter> bean = createConfig().responseCachingFilter();
        Filter filter = bean.getFilter();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final ServletResponse[] capturedResponse = new ServletResponse[1];
        FilterChain chain = (req, res) -> capturedResponse[0] = res;

        filter.doFilter(request, response, chain);

        assertThat(capturedResponse[0])
                .isInstanceOf(org.springframework.web.util.ContentCachingResponseWrapper.class);
    }
}
