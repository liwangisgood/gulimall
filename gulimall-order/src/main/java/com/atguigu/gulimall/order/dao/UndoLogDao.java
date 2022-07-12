package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.UndoLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2022-07-14 21:35:55
 */
@Mapper
public interface UndoLogDao extends BaseMapper<UndoLogEntity> {
	
}
