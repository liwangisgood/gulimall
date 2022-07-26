package com.atguigu.gulimall.product.dao;

import com.atguigu.gulimall.product.entity.AttrEntity;
import com.atguigu.gulimall.product.vo.Attr;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品属性
 * 
 * @author leifengyang
 * @email leifengyang@gmail.com
 * @date 2019-10-01 21:08:49
 */
@Mapper
public interface AttrDao extends BaseMapper<AttrEntity> {

    //从相关serviceImpl中,生成自定义Dao接口对应mapper-sql映射文件 先@Param()参数映射
    //在英文输入法下,再相应生成generate statement即映射文件:alt+shift+enter
    List<Long> selectSearchAttrs(@Param("attrIds") List<Long> attrIds);



}
