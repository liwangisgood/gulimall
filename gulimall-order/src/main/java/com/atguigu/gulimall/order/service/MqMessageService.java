package com.atguigu.gulimall.order.service;

import com.atguigu.gulimall.common.utils.PageUtils;
import com.atguigu.gulimall.order.entity.MqMessageEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 
 *
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2022-07-14 21:35:55
 */
public interface MqMessageService extends IService<MqMessageEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

