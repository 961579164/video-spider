package com.libre.video.core.spider.writer;

import com.google.common.collect.Lists;
import com.libre.video.core.event.VideoEventPublisher;
import com.libre.video.pojo.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author: Libre
 * @Date: 2023/1/14 11:29 PM
 */
@Component
@RequiredArgsConstructor
public class VideoSpiderWriter implements ItemWriter<Video> {

	@Override
	public void write(@NonNull List<? extends Video> videos) throws Exception {
		VideoEventPublisher.publishVideoSaveEvent(Lists.newArrayList(videos));
	}

}
