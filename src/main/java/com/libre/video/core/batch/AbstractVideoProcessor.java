package com.libre.video.core.batch;

import com.libre.core.random.RandomHolder;
import com.libre.video.core.pojo.parse.VideoParse;
import com.libre.video.service.VideoService;
import com.libre.video.toolkit.UserAgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Random;

/**
 * @author: Libre
 * @Date: 2023/1/14 11:11 PM
 */
@Slf4j
public abstract class AbstractVideoProcessor<I extends VideoParse> implements VideoSpiderProcessor<I> {

	protected final VideoService videoService;

	protected final WebClient webClient;

	public AbstractVideoProcessor(VideoService videoService, WebClient webClient) {
		this.videoService = videoService;
		this.webClient = webClient;
		this.initialize();
	}

	protected MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

	private final Random r = RandomHolder.RANDOM;
	protected Mono<String> request(String url) {
		log.info("start request url: {}", url);
		return webClient.get().uri(url).headers(httpHeaders -> httpHeaders.addAll(headers))
			.header("X-Forwarded-For",
				r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256))
			.retrieve().bodyToMono(String.class)
			.doOnError(e -> log.error("request error, url: {},message: {}", url, e.getMessage())).retry(3);
	}

	protected String buildUrl(String urlTemplate, Map<String, Object> params) {
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
		return uriBuilderFactory.uriString(urlTemplate).build(params).toString();
	}

	public void initialize() {
		headers.add(HttpHeaders.ACCEPT,
			"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
		headers.add(HttpHeaders.USER_AGENT, UserAgentContext.getUserAgent());
		headers.add(HttpHeaders.ACCEPT_LANGUAGE, "zh-cn,zh;q=0.5");
		headers.add("Connection", "keep-alive");
	}
}
