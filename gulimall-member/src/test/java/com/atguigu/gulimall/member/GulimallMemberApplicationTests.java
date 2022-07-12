package com.atguigu.gulimall.member;

import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;


@RunWith(SpringRunner.class) //缺少测试方法不可用，忌：勿写错该注解
@SpringBootTest
@Slf4j //该方法测试类似于System.out.println("")
class GulimallMemberApplicationTests {

    @Resource
    MemberService memberService;

    @Test
    void contextLoads() {
        ArrayList<MemberEntity> memberList = new ArrayList<>();
        MemberEntity memberEntity = new MemberEntity();
        memberEntity.setLevelId(1L);
        memberEntity.setUsername("李旺");
        memberList.add(memberEntity);

        MemberEntity memberEntity1 = new MemberEntity();
        memberEntity1.setLevelId(2L);
        memberEntity1.setUsername("Tom");
        memberList.add(memberEntity1);

        boolean result = memberService.saveBatch(memberList);
        if (result){
            log.info("成功插入(猜测是以地址形式输出)："+memberList);//集合ArrayList不是地址形式输出，而是以：[MemberEntity(id=1, levelId=1, username=李旺,
        }else {
            log.info("失败插入，请仔细检查程序！！！");
        }
    }

}
