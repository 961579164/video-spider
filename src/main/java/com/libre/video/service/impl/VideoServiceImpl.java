package com.libre.video.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.libre.boot.autoconfigure.SpringContext;
import com.libre.core.exception.LibreException;
import com.libre.core.toolkit.CollectionUtil;
import com.libre.core.toolkit.StringPool;
import com.libre.core.toolkit.StringUtil;
import com.libre.video.config.VideoProperties;
import com.libre.video.constant.SystemConstants;
import com.libre.video.core.spider.VideoSpiderJobBuilder;
import com.libre.video.core.download.M3u8Download;
import com.libre.video.core.download.VideoEncoder;
import com.libre.video.core.enums.RequestTypeEnum;
import com.libre.video.core.event.VideoUploadEvent;
import com.libre.video.core.pojo.dto.VideoRequestParam;
import com.libre.video.mapper.VideoMapper;
import com.libre.video.pojo.Video;
import com.libre.video.pojo.dto.VideoQuery;
import com.libre.video.service.VideoService;
import com.libre.video.toolkit.ThreadPoolUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

	private final VideoEncoder videoEncoder;

	private final ElasticsearchOperations elasticsearchOperations;

	private final M3u8Download m3u8Download;

	private final VideoProperties properties;

	private final JobLauncher jobLauncher;

	private final VideoSpiderJobBuilder videoSpiderJobBuilder;

	private final VideoProperties videoProperties;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void download(List<Long> ids) {
		for (Long id : ids) {
			videoEncoder.encodeAndWrite(id);
		}
	}

	public void saveVideoToLocal(VideoUploadEvent event) {
		Video video = event.getVideo();
		Resource resource = event.getResource();
		String downloadPath = properties.getDownloadPath();
		try (InputStream in = resource.getInputStream()) {
			Files.copy(in, Path.of(downloadPath + video.getVideoPath()), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Exception e) {
			log.error("文件拷贝失败, ", e);
		}
		this.updateById(video);
		log.info("video save success, url: {}", video.getVideoPath());
	}

	// @Override
	// public void saveVideoToOss(VideoUploadEvent event) {
	// Video video = event.getVideo();
	// String videoPath = video.getVideoPath();
	// Resource resource = event.getResource();
	//
	// Assert.hasText(videoPath, "video path must not be null");
	// Assert.notNull(resource, "video resource must not be null");
	//
	// try (InputStream inputStream = resource.getInputStream()) {
	// ossTemplate.putObject(SystemConstants.VIDEO_BUCKET_NAME, videoPath, inputStream);
	// }
	// catch (IOException e) {
	// throw new LibreException("文件上传失败: " + e.getMessage());
	// }
	// this.updateById(video);
	// log.info("video save success, url: {}", video.getVideoPath());
	// }

	@Override
	@SuppressWarnings("unchecked")
	public Page<Video> findByPage(PageDTO<Video> page, VideoQuery videoQuery) {
		PageRequest pageRequest = PageRequest.of((int) page.getCurrent(), (int) page.getSize());

		NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();

		String title = videoQuery.getTitle();
		if (StringUtil.isNotBlank(title)) {
			BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
			List<QueryBuilder> shouldQuery = boolQueryBuilder.should();

			MatchPhraseQueryBuilder matchPhraseQuery = QueryBuilders.matchPhraseQuery("title", title);
			matchPhraseQuery.boost(10);
			shouldQuery.add(matchPhraseQuery);

			QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery(title);
			stringQuery.field("title")
				.field("title.keyword")
				.queryName(title)
				.fuzziness(Fuzziness.AUTO)
				.fuzzyPrefixLength(2)
				.fuzzyMaxExpansions(20)
				.fuzzyTranspositions(true)
				.allowLeadingWildcard(false);
			stringQuery.boost(9);
			shouldQuery.add(stringQuery);

			PrefixQueryBuilder prefixQuery = QueryBuilders.prefixQuery("title.keyword", title);
			shouldQuery.add(prefixQuery);

			MatchPhrasePrefixQueryBuilder matchPhrasePrefixQueryBuilder = QueryBuilders.matchPhrasePrefixQuery("title", title);
			shouldQuery.add(matchPhrasePrefixQueryBuilder);

			MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("title", title);
			matchQuery.boost(1);
			shouldQuery.add(matchQuery);

			nativeSearchQueryBuilder.withQuery(boolQueryBuilder);
		}

	    nativeSearchQueryBuilder.withTrackTotalHits(true);
		nativeSearchQueryBuilder.withPageable(pageRequest);
		//nativeSearchQueryBuilder.withSorts(sortBuilders(page));
		NativeSearchQuery query = nativeSearchQueryBuilder.build();

		SearchHits<Video> hits = elasticsearchOperations.search(query, Video.class);
		SearchPage<Video> searchPage = SearchHitSupport.searchPageFor(hits, query.getPageable());
		return (Page<Video>) SearchHitSupport.unwrapSearchHits(searchPage);
	}

	@Override
	public String watch(Long videoId) throws IOException {
		log.info("video watch id is: {}", videoId);
		Video video = Optional.ofNullable(this.getById(videoId))
				.orElseThrow(() -> new LibreException(String.format("video not exist, videoId: %d", videoId)));

		String realUrl = video.getRealUrl();
		if (StringUtil.isBlank(realUrl)) {
			throw new LibreException("realUrl is blank, realUrl: " + realUrl);
		}
		String requestBaseUrl = getRequestBaseUrl(realUrl);

		if (StringUtil.isBlank(requestBaseUrl)) {
			throw new LibreException("requestBaseUrl is blank, realUrl: " + realUrl);
		}
		String m3u8Content = video.getM3u8Content();
		if (StringUtil.isBlank(m3u8Content)) {
			try {
				m3u8Download.downloadAndReadM3u8File(video);
			}
			catch (Exception e) {
				throw new LibreException(e);
			}
			this.updateById(video);
			m3u8Content = video.getM3u8Content();
		}
		String[] lines = StringUtils.split(m3u8Content, StringPool.NEWLINE);
		List<String> m3u8Lines = Lists.newArrayList();
		for (String line : lines) {
			if (line.endsWith(SystemConstants.TS_SUFFIX)) {
				line = requestBaseUrl + StringPool.SLASH + line;
			}
			m3u8Lines.add(line);
		}

		String videoTempDir = m3u8Download.getVideoTempDir(videoId);
		String m3u8FilePath = videoTempDir + File.separator + videoId + SystemConstants.MU38_SUFFIX;
		String watchPath = videoId + File.separator + videoId + SystemConstants.MU38_SUFFIX;
		if (Files.exists(Paths.get(m3u8FilePath))) {
			return watchPath;
		}

		Path tempPathDir = Paths.get(videoTempDir);
		if (!Files.exists(tempPathDir)) {
			Files.createDirectory(tempPathDir);
		}
		FileUtils.writeLines(new File(m3u8FilePath), m3u8Lines);
		return watchPath;
	}

	private static String getRequestBaseUrl(String realUrl) {
		String requestBaseUrl = null;
		if (realUrl.contains(StringPool.QUESTION_MARK)) {
			int questionMarkIndex = realUrl.indexOf(StringPool.QUESTION_MARK);
			realUrl = realUrl.substring(0, questionMarkIndex);
			requestBaseUrl = realUrl.substring(0, realUrl.lastIndexOf(StringPool.SLASH));
		}
		else if (realUrl.endsWith(SystemConstants.MU38_SUFFIX)) {
			requestBaseUrl = realUrl.substring(0, realUrl.lastIndexOf(StringPool.SLASH));
		}
		return requestBaseUrl;
	}

	private List<SortBuilder<?>> sortBuilders(PageDTO<Video> page) {
		List<SortBuilder<?>> sortBuilders = Lists.newArrayList();
		List<OrderItem> orderItems = page.getOrders();
		if (CollectionUtil.isEmpty(orderItems)) {
			FieldSortBuilder fieldSortBuilder = SortBuilders.fieldSort("publishTime").order(SortOrder.DESC);
			sortBuilders.add(fieldSortBuilder);
		}

		for (OrderItem orderItem : orderItems) {
			String column = orderItem.getColumn();
			boolean asc = orderItem.isAsc();
			FieldSortBuilder fieldSortBuilder = SortBuilders.fieldSort(column);
			if (!asc) {
				fieldSortBuilder.order(SortOrder.DESC);
			}
			sortBuilders.add(fieldSortBuilder);
		}
		return sortBuilders;
	}

	@Override
	public void syncToElasticsearch() {
		log.info("开始同步数据....");
		Job esSyncJob = SpringContext.getBean("esSyncJob");
		Assert.notNull(esSyncJob, "esSyncJob must not be null");
		try {
			jobLauncher.run(esSyncJob, createJobParams());
		}
		catch (Exception e) {
			throw new LibreException(e);
		}
	}

	@Override
	public void spider(Integer type) {
		Job videoSpiderJob = videoSpiderJobBuilder.videoSpiderJob(type);
		try {
			jobLauncher.run(videoSpiderJob, createJobParams());
		}
		catch (Exception e) {
			throw new LibreException(e);
		}
	}

	private static JobParameters createJobParams() {
		return new JobParametersBuilder().addDate("date", new Date()).toJobParameters();
	}

	@Override
	public void shutdown() {
		ThreadPoolTaskExecutor executor = ThreadPoolUtil.videoRequestExecutor();
		executor.shutdown();
		executor.initialize();
	}

}
