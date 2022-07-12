package com.atguigu.gulimall.product;

import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.service.BrandService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;


@RunWith(SpringRunner.class) //不添加无法发现测试方法
@SpringBootTest
class GulimallProductApplicationTests {

    @Resource
    BrandService brandService;

    @Test
    void contextLoads() {
        List<BrandEntity> list = brandService.list();
        int count = 0;
        for (BrandEntity brandEntity : list) {
            System.out.println(++count + ":" + brandEntity);

        }
    }

}
