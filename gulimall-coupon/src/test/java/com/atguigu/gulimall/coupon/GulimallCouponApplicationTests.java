package com.atguigu.gulimall.coupon;

import com.atguigu.gulimall.coupon.entity.CouponEntity;
import com.atguigu.gulimall.coupon.service.CouponService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest
class GulimallCouponApplicationTests {

    @Resource
    CouponService couponService;

    @Test
    void contextLoads() {

        CouponEntity couponEntity = new CouponEntity();
        couponEntity.setId(333L);
        //couponEntity.setCouponType(333); // smallint 对应byte 插入333数据出现异常：Data truncation: Out of range value for column 'coupon_type' at row 1
        couponEntity.setCouponType(100);
        boolean save = couponService.save(couponEntity);

        //数据库中最大id=11号延后一位id=12，忽略主键形式插入，最好用日志@Slf4j
        if (save){
            System.out.println("成功插入："+couponEntity);//成功插入：CouponEntity(id=12, couponType=100, couponImg=null, couponName=null, num=null, amount=null, perLimit=null, minPoint=null, startTime=null, endTime=null, useType=null, note=null, publishCount=null, useCount=null, receiveCount=null, enableStartTime=null, enableEndTime=null, code=null, memberLevel=null, publish=null)
        }else {
            System.out.println("插入失败，仔细检查!!!");
        }

    }

}
