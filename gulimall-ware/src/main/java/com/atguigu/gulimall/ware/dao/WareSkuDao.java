package com.atguigu.gulimall.ware.dao;

import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品库存
 * 
 * @author leifengyang
 * @email leifengyang@gmail.com
 * @date 2019-10-08 09:59:40
 */
@Mapper
public interface WareSkuDao extends BaseMapper<WareSkuEntity> {

    void addStock(@Param("skuId") Long skuId, @Param("wareId") Long wareId, @Param("skuNum") Integer skuNum);

    /**
     * java代码Dao接口中方法参数可用基本数据类型 sql语句中得用其对应引用数据类型,如long->Long
     * 一个参数可以不用@Param("skuId")进行标注,多个参数一定要用@Param("参数名")进行标注
     */
    Long getSkuStock(@Param("skuId") long skuId);
}
