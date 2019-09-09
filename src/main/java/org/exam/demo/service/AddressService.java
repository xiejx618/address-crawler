package org.exam.demo.service;

import cn.edu.hfut.dmic.webcollector.conf.Configuration;
import cn.edu.hfut.dmic.webcollector.model.CrawlDatum;
import cn.edu.hfut.dmic.webcollector.model.CrawlDatums;
import cn.edu.hfut.dmic.webcollector.model.Page;
import cn.edu.hfut.dmic.webcollector.plugin.rocks.BreadthCrawler;
import cn.edu.hfut.dmic.webcollector.util.ExceptionUtils;
import org.exam.demo.domain.Address;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AddressService extends BreadthCrawler {
    private static final Logger logger = LoggerFactory.getLogger(AddressService.class);
    @Resource
    private JdbcOperations jdbcOperations;

    public AddressService() {
        super("logs", false);
    }

    public void start() {
        addSeedAndReturn("http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2018/");
        setConf(Configuration.getDefault()
                //默认是3秒
                .setConnectTimeout(10000)
                //默认是10秒
                .setReadTimeout(20000)
                //默认是1分钟，如果一个线程等了5分钟就会被杀掉,所以线程sleep时要注意下
                .setWaitThreadEndTime(300000)
        );
        setMaxExecuteCount(5);
        setThreads(1);//跑得太快会被导向做验证码校验
        try {
            start(6);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param type 是否解析当前页。type可以是province,city,county,town,village
     *             如Arrays.asList("province", "city", "county").contains(type)
     *             代表以上级别才去解析
     */
    private static boolean matchVisit(String type) {
        return true;
    }

    /**
     * @param type 是否添加到后面去爬.type可以是:province,city,county,town,village
     *             如Arrays.asList("province", "city", "county").contains(type);
     *             当前页级别是上面，才添加链接到后面去爬
     */
    private static boolean matchNext(String type) {
        return true;
    }

    @Override
    public void visit(Page page, CrawlDatums next) {
        CrawlDatum datum = page.crawlDatum();
        try {
            Elements elements = getElements(page);
            if (elements == null) return;
            List<Address> items = new ArrayList<>();
            handle(next, datum, elements, items);
            save(items);
        } catch (Exception e) {
            logger.error("处理出错了:" + e.getMessage());
            // 当捕捉到异常时，且认为这个网页需要重新爬取时,应该使用ExceptionUtils.fail(e)
            // 无视或者throw异常在编译时会报错，因为visit方法没有throws异常
            // 该方法会抛出RuntimeException，不会强制要求visit方法加上throws
            ExceptionUtils.fail(e);
        }
    }

    private static Elements getElements(Page page) {
        CrawlDatum datum = page.crawlDatum();
        //gbk不乱码
        page.charset("GB18030");
        //根据内容判断是什么级别数据
        String cssSelector;
        if (page.select("table.villagetable").size() > 0) {
            datum.type("village");
            cssSelector = ".villagetable .villagetr td";
        } else if (page.select("table.towntable").size() > 0) {
            datum.type("town");
            cssSelector = ".towntable .towntr td";
        } else if (page.select("table.countytable").size() > 0) {
            datum.type("county");
            cssSelector = ".countytable .countytr td";
        } else if (page.select("table.citytable").size() > 0) {
            datum.type("city");
            cssSelector = ".citytable .citytr td";
        } else if (page.select("table.provincetable").size() > 0) {
            datum.type("province");
            cssSelector = ".provincetable .provincetr td";
        } else {
            //如果弹出验证码或者其它原因,就等一分钟
            try {
                TimeUnit.MINUTES.sleep(1);
            } catch (InterruptedException e) {
                logger.info("一分钟的等待被打断了");
            }
            throw new RuntimeException("Unsupported page!\naddSeedAndReturn(\"" + datum.url() + "\");");
        }
        if (!matchVisit(datum.type())) {
            return null;
        }
        Elements elements = page.select(cssSelector);
        //不该出现找不到元素的页面或者其它原因,就等一分钟.
        if (elements.size() == 0) {
            try {
                TimeUnit.MINUTES.sleep(1);
            } catch (InterruptedException e) {
                logger.info("一分钟的等待被打断了");
            }
            throw new RuntimeException("Zero size,code:" + datum.code() + "\naddSeedAndReturn(\"" + datum.url() + "\").type(\"" + datum.type() + "\");");
        }
        return elements;
    }

    private static void handle(CrawlDatums next, CrawlDatum datum, Elements elements, List<Address> items) {
        String type = datum.type();
        switch (type) {
            case "province":
                for (Element element : elements) {
                    element = element.getElementsByTag("a").first();
                    String href = element.attr("href");
                    String id = href.substring(0, href.lastIndexOf(".html")) + "0000000000";
                    String name = element.text();
                    String url = element.attr("abs:href");
                    handleItem(next, datum, id, name, url, items);
                }
                break;
            case "city":
            case "county":
            case "town":
                for (int i = 0; i < elements.size(); i = i + 2) {
                    Element idElement = elements.get(i);
                    Element nameElement = elements.get(i + 1);
                    String id = idElement.text();
                    String name = nameElement.text();
                    String url = idElement.getElementsByTag("a").attr("abs:href");
                    handleItem(next, datum, id, name, url, items);
                }
                break;
            case "village":
                for (int i = 0; i < elements.size(); i = i + 3) {
                    Element idElement = elements.get(i);
                    Element nameElement = elements.get(i + 2);
                    String id = idElement.text();
                    String name = nameElement.text();
                    String url = idElement.getElementsByTag("a").attr("abs:href");
                    handleItem(next, datum, id, name, url, items);
                }
                break;
        }
    }

    private static void handleItem(CrawlDatums next, CrawlDatum datum, String id, String name, String url, List<Address> items) {
        String type = datum.type();
        //写入数据
        Address address = new Address(type, id, name,
                datum.meta("provinceId"), datum.meta("provinceName"),
                datum.meta("cityId"), datum.meta("cityName"),
                datum.meta("countyId"), datum.meta("countyName"),
                datum.meta("townId"), datum.meta("townName"));
        items.add(address);
        //添加到下一步去爬
        if (StringUtils.hasText(url) && matchNext(type)) {
            //type由爬出来的page检测
            CrawlDatum nextDatum = new CrawlDatum(url)
                    .meta("provinceId", address.getProvinceId()).meta("provinceName", address.getProvinceName())
                    .meta("cityId", address.getCityId()).meta("cityName", address.getCityName())
                    .meta("countyId", address.getCountyId()).meta("countyName", address.getCountyName())
                    .meta("townId", address.getTownId()).meta("townName", address.getTownName());
            if ("province".equals(type)) {
                nextDatum.meta("provinceId", id).meta("provinceName", name);
            } else if ("city".equals(type)) {
                nextDatum.meta("cityId", id).meta("cityName", name);
            } else if ("county".equals(type)) {
                nextDatum.meta("countyId", id).meta("countyName", name);
            } else if ("town".equals(type)) {
                nextDatum.meta("townId", id).meta("townName", name);
            } else {
                throw new UnsupportedOperationException("type error:" + type);
            }
            next.add(nextDatum);
        }
    }

    /**
     * 插入数据库
     */
    private void save(List<Address> list) {
        try {
            jdbcOperations.batchUpdate("insert into address (type, id, name, province_id, province_name, city_id, city_name, county_id, county_name, town_id, town_name) values (?,?,?,?,?,?,?,?,?,?,?)",
                    list, list.size(), (ps, address) -> {
                        int i = 1;
                        ps.setString(i++, address.getType());
                        ps.setString(i++, address.getId());
                        ps.setString(i++, address.getName());
                        ps.setString(i++, address.getProvinceId());
                        ps.setString(i++, address.getProvinceName());
                        ps.setString(i++, address.getCityId());
                        ps.setString(i++, address.getCityName());
                        ps.setString(i++, address.getCountyId());
                        ps.setString(i++, address.getCountyName());
                        ps.setString(i++, address.getTownId());
                        ps.setString(i, address.getTownName());
                    });
        } catch (DataAccessException e) {
            logger.error("批量插入数据库出错", e);
            try (FileOutputStream out = new FileOutputStream("err.csv", true)) {
                StringBuilder sb = new StringBuilder();
                for (Address a : list) {
                    sb.append(a.getType())
                            .append(",").append(a.getId()).append(",").append(a.getName())
                            .append(",").append(a.getProvinceId()).append(",").append(a.getProvinceName())
                            .append(",").append(a.getCityId()).append(",").append(a.getCityName())
                            .append(",").append(a.getCountyId()).append(",").append(a.getCountyName())
                            .append(",").append(a.getTownId()).append(",").append(a.getTownName())
                            .append("\n");
                }
                FileCopyUtils.copy(sb.toString().getBytes(StandardCharsets.UTF_8), out);
            } catch (IOException ex) {
                logger.error("数据写入err.csv出错", e);
            }
        }
    }
}
