package com.atguigu.gulimall.search;


import com.alibaba.fastjson.JSON;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;

@SpringBootTest
@RunWith(SpringRunner.class)//表明Test测试类要使用注入到容器的类
class GulimallSearchApplicationTests {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    /**
     * 测试数据 存储数据
     * 同时更新也支持
     */
    @Test
    public void indexData() throws IOException {
        User user = new User("李旺", "male", 22);
        IndexRequest indexRequest = new IndexRequest("user");
        String s = JSON.toJSONString(user);
        System.out.println(restHighLevelClient + " " + user + " " + s);
        indexRequest.id("1");
        //要设置内容类型,否则:java.lang.IllegalArgumentException: The number of object passed must be even but was [1]
        indexRequest.source(s, XContentType.JSON);
        //网络操作都会有异常 需要抛出
        IndexResponse index = restHighLevelClient.index(indexRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        restHighLevelClient.close();//可以使用完关闭释放资源
    }

    //query match检索
    @Test
    public void searchData() throws IOException {
        //创建检索对象
        SearchRequest searchRequest = new SearchRequest();
        //指定索引
        searchRequest.indices("bank");
        //指定DSL 检索条件
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("address", "mill"));
        System.out.println("打印出检索条件:" + searchSourceBuilder.toString());
        searchRequest.source(searchSourceBuilder);
        //执行检索
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
        System.out.println(searchResponse);

    }

    /**
     * 检索聚合操作
     */
    @Test
    public void searchDataByAggs() throws IOException {
        SearchRequest searchRequest = new SearchRequest("bank");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("address", "mill"));
        //按照年龄的值分布进行聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("ageAgg").field("age").size(4);
        searchSourceBuilder.aggregation(termsAggregationBuilder);
        //计算平均薪资
        AvgAggregationBuilder avgAggregationBuilder = AggregationBuilders.avg("balanceAvg").field("balance");
        searchSourceBuilder.aggregation(avgAggregationBuilder);

        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);
        System.out.println("打印出检索条件:" + searchSourceBuilder);
        System.out.println(searchResponse);
        for (SearchHit responseHit : searchResponse.getHits()) {
            String sourceAsString = responseHit.getSourceAsString();
            Account account = JSON.parseObject(sourceAsString, Account.class);
            System.out.println("Account:" + account);


        }


    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Account {
        private int account_number;
        private int balance;
        private String firstname;
        private String lastname;
        private int age;
        private String gender;
        private String address;
        private String employer;
        private String email;
        private String city;
        private String state;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class User {
        private String userName;
        private String gender;
        private Integer age;
    }


    /**
     * GET请求查询某一文档
     * 1. throws IOException: 找不到 不会抛出异常 返回find false信息
     * 2.try catch捕获处理异常: 不会执行 e.printStackTrace();
     * 3.请求版本不同不回引发异常抛出 NOT_FOUND
     *
     * @throws IOException
     */
    @Test
    public void testGetRequest() throws IOException {

        GetRequest getRequest = new GetRequest(
                "bank",
                "1");
        GetResponse getResponse = null;

        //三条语句一起使用 返回是否有响应数据查询结果
        getRequest.fetchSourceContext(new FetchSourceContext(false));//关闭查询获取的数据
        getRequest.storedFields("_none_");
        boolean exists = restHighLevelClient.exists(getRequest, RequestOptions.DEFAULT);
        System.out.println("exists:" + exists);

        getResponse = restHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
        System.out.println(getResponse);
  /*      结果只会返回如下,缺少"_source":{}
        {
            "_index": "bank",
                "_type": "_doc",
                "_id": "1",
                "_version": 1,
                "_seq_no": 0,
                "_primary_term": 1,
                "found": true
        }*/


//        GetRequest request = new GetRequest("does_not_exist", "1").version(34L);
//        GetResponse getResponse = null;
//        try {
//            getResponse = restHighLevelClient.get(request, RequestOptions.DEFAULT);
//        } catch (ElasticsearchException e) {
//            if (e.status() == RestStatus.NOT_FOUND) {
//                System.out.println(RestStatus.NOT_FOUND);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


    //默认同步删除操作
    @Test
    public void testDeleteRequest() throws IOException {
        DeleteRequest request = new DeleteRequest(
                "shopping",
                "1");
        DeleteResponse deleteResponse = restHighLevelClient.delete(
                request, RequestOptions.DEFAULT);
        System.out.println(deleteResponse);
    }//DeleteResponse[index=shopping,type=_doc,id=1,version=3,result=deleted,shards=ShardInfo{total=2, successful=1, failures=[]}]

    //异步删除操作
    @Test
    public void testDeleteRequest2() throws InterruptedException {
        ActionListener<DeleteResponse> actionListener = new ActionListener<DeleteResponse>() {

            //尾部加上线程睡眠 成功onResponse()
            //onResponse()方法执行
            //DeleteResponse[index=shopping,type=_doc,id=1,version=2,result=deleted,shards=ShardInfo{total=2, successful=1, failures=[]}]
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                System.out.println("onResponse()方法执行");
                System.out.println(deleteResponse);
            }

            //尾部没加线程睡眠 会抛出
//            rg.apache.http.ConnectionClosedException: Connection is closed
            @Override
            public void onFailure(Exception e) {
                System.out.println("onFailure()方法执行");
                e.printStackTrace();
            }
        };

        DeleteRequest request = new DeleteRequest(
                "shopping",
                "1");
        restHighLevelClient.deleteAsync(request, RequestOptions.DEFAULT, actionListener);
        Thread.sleep(2000);//具体加多少看返回数据量大小 进行延时
    }


    /**
     * ES更新部分数据 响应返回数据默认不包含_source{}数据
     * 要更新的内容原先不存在,如:id不存在,就会抛出异常: Suppressed: org.elasticsearch.client.ResponseException Not Found
     * @throws IOException
     */
    @Test
    public void testUpdateRequest() throws IOException {
        UpdateRequest request = new UpdateRequest(
                "shopping",
                "22").doc("title","华为荣耀手机");

        UpdateResponse updateResponse = restHighLevelClient.update(
                request, RequestOptions.DEFAULT);

        System.out.println(updateResponse);//UpdateResponse[index=shopping,type=_doc,id=2,version=3,seqNo=8,primaryTerm=3,result=updated,shards=ShardInfo{total=2, successful=1, failures=[]}]
    }


    /**
     * 索引id不存在进行部分字段更新,创建了不存在的id对应的字段,返回数据_source{}中也只有该更新的字段
     * 其他返回与操作均与testUpdateRequest()相似
     */
    @Test
    public void testUpsertRequest() throws IOException {
        UpdateRequest request = new UpdateRequest(
                "shopping",
                "1").doc("title","黑鲨手机");

        //无论索引 id是否存在都要进行更新
        request.docAsUpsert(true);

        UpdateResponse updateResponse = restHighLevelClient.update(
                request, RequestOptions.DEFAULT);

        System.out.println(updateResponse);//UpdateResponse[index=shopping,type=_doc,id=1,version=1,seqNo=9,primaryTerm=3,result=created,shards=ShardInfo{total=2, successful=1, failures=[]}]

    }


}
