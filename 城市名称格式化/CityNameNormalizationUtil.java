
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 城市名格式化工具类
 */
public class CityNameNormalizationUtil {

    private static final Logger logger = LoggerFactory.getLogger(CityNameNormalizationUtil.class);

    private static DatabaseReader reader;

    private CityNameNormalizationUtil() {
    }

    public static CityNameNormalizationUtil getInstance(String geoIpDatabasePath) throws IOException {
        if (reader == null) {
            reader = new DatabaseReader.Builder(new File(geoIpDatabasePath)).build();
        }
        return new CityNameNormalizationUtil();
    }

    public static void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
        reader = null;
    }

    public String parseCityFromIp(String ip) throws IOException, GeoIp2Exception {
        InetAddress address = InetAddress.getByName(ip);
        CityResponse response = reader.city(address);

        // 获取国家信息
        Country country = response.getCountry();
        String countryName = country.getName();

        // 获取省份
        Subdivision subdivision = response.getMostSpecificSubdivision();

        // 获取城市
        City city = response.getCity();

        // 国内的城市需要特别处理
        if (Objects.equals(countryName, "China")) {
            String provinceName = subdivision.getNames().getOrDefault("zh-CN", "");
            String cityName = city.getNames().getOrDefault("zh-CN", "");
            return getBestMatchCity(provinceName + cityName);
        } else {
            // 国外的城市
            String subdivisionName = subdivision.getName();
            String cityName = city.getName();
            return countryName+" "+subdivisionName+" "+cityName;
        }
    }

    /**
     * 获取最佳匹配城市
     */
    public static String getBestMatchCity(String inputCity) {
        if (StringUtils.isBlank(inputCity)) {
            return null;
        }

        //计算最小编辑距离，注意是有序返回的
        List<String> cities = new ArrayList<>(cityMap.keySet());
        List<Integer> distances = cities.parallelStream()
                .map((keyword) -> damerauLevenshteinDistance(inputCity, keyword))
                .collect(Collectors.toList());

        Integer minDistance = null;
        String bestCity = null;
        for (int i = 0; i < distances.size(); i++) {
            // 最佳匹配
            if (distances.get(i) == 0) {
                return cityMap.get(cities.get(i));
            }
            // 寻找最小距离
            else if (minDistance == null || minDistance > distances.get(i)) {
                minDistance = distances.get(i);
                bestCity = cityMap.get(cities.get(i));
            }
        }

        logger.debug("[getBestMatchCity] inputCity:{} bestCity:{}", inputCity, bestCity);
        return bestCity;
    }

    /**
     * 计算文本相似度
     */
    private static int damerauLevenshteinDistance(String str1, String str2){
        int m = str1.length();
        int n = str2.length();
        // 构造(m+1, n*1)的二维数组
        int d[][] = new int[m+1][n+1];
        // 初始化第 1 列
        for (int i=0; i<=m; i++) {
            d[i][0] = i;
        }
        // 初始化第 1 行
        for (int j=0; j<=n; j++) {
            d[0][j] = j;
        }

        // 自底向上递推计算每个 d[i][j] 的值
        for (int i=1; i<=m; i++) {
            for (int j=1; j<=n; j++) {
                if (Objects.equals(str1.charAt(i - 1), str2.charAt(j - 1))) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    d[i][j] = Integer.min(Integer.min(d[i - 1][j], d[i][j - 1]),d[i - 1][j - 1]) + 1;
                    if (i > 1 && j > 1 && Objects.equals(str1.charAt(i - 1), str2.charAt(j - 2)) && Objects.equals(str1.charAt(i - 2), str2.charAt(j - 1))) {
                        d[i][j] = Integer.min(d[i][j], d[i - 2][j - 2] + 1);
                    }
                }
            }
        }
        int distance = d[m][n];
        logger.debug("[damerauLevenshteinDistance] {}&{}:{}", str1, str2, distance);
        return distance;
    }

    // 使用插入顺序
    private static Map<String, String> cityMap = new LinkedHashMap<>();
    static {
        // 直辖市
        cityMap.put("北京市北京", "北京");
        cityMap.put("天津市天津", "天津");
        cityMap.put("重庆市重庆", "重庆");
        cityMap.put("北京东城", "北京");
        cityMap.put("北京西城", "北京");
        cityMap.put("北京崇文", "北京");
        cityMap.put("北京宣武", "北京");
        cityMap.put("北京朝阳", "北京");
        cityMap.put("北京石景山", "北京");
        cityMap.put("北京海淀", "北京");
        cityMap.put("北京门头沟", "北京");
        cityMap.put("北京房山", "北京");
        cityMap.put("北京通州", "北京");
        cityMap.put("北京顺义", "北京");
        cityMap.put("北京昌平", "北京");
        cityMap.put("北京大兴", "北京");
        cityMap.put("北京怀柔", "北京");
        cityMap.put("北京平谷", "北京");
        cityMap.put("北京密云", "北京");
        cityMap.put("北京延庆", "北京");
        cityMap.put("上海黄浦", "上海");
        cityMap.put("上海卢湾", "上海");
        cityMap.put("上海徐汇", "上海");
        cityMap.put("上海长宁", "上海");
        cityMap.put("上海静安", "上海");
        cityMap.put("上海普陀", "上海");
        cityMap.put("上海闸北", "上海");
        cityMap.put("上海虹口", "上海");
        cityMap.put("上海杨浦", "上海");
        cityMap.put("上海闵行", "上海");
        cityMap.put("上海宝山", "上海");
        cityMap.put("上海嘉定", "上海");
        cityMap.put("上海浦东", "上海");
        cityMap.put("上海金山", "上海");
        cityMap.put("上海松江", "上海");
        cityMap.put("上海青浦", "上海");
        cityMap.put("上海南汇", "上海");
        cityMap.put("上海奉贤", "上海");
        cityMap.put("上海崇明", "上海");
        cityMap.put("天津和平", "天津");
        cityMap.put("天津河东", "天津");
        cityMap.put("天津河西", "天津");
        cityMap.put("天津南开", "天津");
        cityMap.put("天津河北", "天津");
        cityMap.put("天津红桥", "天津");
        cityMap.put("天津塘沽", "天津");
        cityMap.put("天津汉沽", "天津");
        cityMap.put("天津大港", "天津");
        cityMap.put("天津东丽", "天津");
        cityMap.put("天津西青", "天津");
        cityMap.put("天津津南", "天津");
        cityMap.put("天津北辰", "天津");
        cityMap.put("天津武清", "天津");
        cityMap.put("天津宝坻", "天津");
        cityMap.put("天津宁河", "天津");
        cityMap.put("天津静海", "天津");
        cityMap.put("天津蓟县", "天津");
        cityMap.put("重庆万州", "重庆");
        cityMap.put("重庆涪陵", "重庆");
        cityMap.put("重庆渝中", "重庆");
        cityMap.put("重庆大渡口", "重庆");
        cityMap.put("重庆江北", "重庆");
        cityMap.put("重庆沙坪坝", "重庆");
        cityMap.put("重庆九龙坡", "重庆");
        cityMap.put("重庆南岸", "重庆");
        cityMap.put("重庆北碚", "重庆");
        cityMap.put("重庆万盛", "重庆");
        cityMap.put("重庆双桥", "重庆");
        cityMap.put("重庆渝北", "重庆");
        cityMap.put("重庆巴南", "重庆");
        cityMap.put("重庆黔江", "重庆");
        cityMap.put("重庆长寿", "重庆");
        cityMap.put("重庆綦江", "重庆");
        cityMap.put("重庆潼南", "重庆");
        cityMap.put("重庆铜梁", "重庆");
        cityMap.put("重庆大足", "重庆");
        cityMap.put("重庆荣昌", "重庆");
        cityMap.put("重庆璧山", "重庆");
        cityMap.put("重庆梁平", "重庆");
        cityMap.put("重庆城口", "重庆");
        cityMap.put("重庆丰都", "重庆");
        cityMap.put("重庆垫江", "重庆");
        cityMap.put("重庆武隆", "重庆");
        cityMap.put("重庆忠县", "重庆");
        cityMap.put("重庆开县", "重庆");
        cityMap.put("重庆云阳", "重庆");
        cityMap.put("重庆奉节", "重庆");
        cityMap.put("重庆巫山", "重庆");
        cityMap.put("重庆巫溪", "重庆");
        cityMap.put("重庆石柱", "重庆");
        cityMap.put("重庆秀山", "重庆");
        cityMap.put("重庆酉阳", "重庆");
        cityMap.put("重庆彭水", "重庆");
        cityMap.put("重庆江津", "重庆");
        cityMap.put("重庆合川", "重庆");
        cityMap.put("重庆永川", "重庆");
        cityMap.put("重庆南川", "重庆");
        // 省市
        cityMap.put("河北石家庄", "河北-石家庄");
        cityMap.put("河北唐山", "河北-唐山");
        cityMap.put("河北秦皇岛", "河北-秦皇岛");
        cityMap.put("河北邯郸", "河北-邯郸");
        cityMap.put("河北邢台", "河北-邢台");
        cityMap.put("河北保定", "河北-保定");
        cityMap.put("河北张家口", "河北-张家口");
        cityMap.put("河北承德", "河北-承德");
        cityMap.put("河北沧州", "河北-沧州");
        cityMap.put("河北廊坊", "河北-廊坊");
        cityMap.put("河北衡水", "河北-衡水");
        cityMap.put("山西太原", "山西-太原");
        cityMap.put("山西大同", "山西-大同");
        cityMap.put("山西阳泉", "山西-阳泉");
        cityMap.put("山西长治", "山西-长治");
        cityMap.put("山西晋城", "山西-晋城");
        cityMap.put("山西朔州", "山西-朔州");
        cityMap.put("山西晋中", "山西-晋中");
        cityMap.put("山西运城", "山西-运城");
        cityMap.put("山西忻州", "山西-忻州");
        cityMap.put("山西临汾", "山西-临汾");
        cityMap.put("山西吕梁", "山西-吕梁");
        cityMap.put("内蒙古呼和浩特", "内蒙古-呼和浩特");
        cityMap.put("内蒙古包头", "内蒙古-包头");
        cityMap.put("内蒙古乌海", "内蒙古-乌海");
        cityMap.put("内蒙古赤峰", "内蒙古-赤峰");
        cityMap.put("内蒙古通辽", "内蒙古-通辽");
        cityMap.put("内蒙古鄂尔多斯", "内蒙古-鄂尔多斯");
        cityMap.put("内蒙古呼伦贝尔", "内蒙古-呼伦贝尔");
        cityMap.put("内蒙古巴彦淖尔", "内蒙古-巴彦淖尔");
        cityMap.put("内蒙古乌兰察布", "内蒙古-乌兰察布");
        cityMap.put("内蒙古兴安盟", "内蒙古-兴安盟");
        cityMap.put("内蒙古锡林郭勒", "内蒙古-锡林郭勒");
        cityMap.put("内蒙古阿拉善", "内蒙古-阿拉善");
        cityMap.put("辽宁沈阳", "辽宁-沈阳");
        cityMap.put("辽宁大连", "辽宁-大连");
        cityMap.put("辽宁鞍山", "辽宁-鞍山");
        cityMap.put("辽宁抚顺", "辽宁-抚顺");
        cityMap.put("辽宁本溪", "辽宁-本溪");
        cityMap.put("辽宁丹东", "辽宁-丹东");
        cityMap.put("辽宁锦州", "辽宁-锦州");
        cityMap.put("辽宁营口", "辽宁-营口");
        cityMap.put("辽宁阜新", "辽宁-阜新");
        cityMap.put("辽宁辽阳", "辽宁-辽阳");
        cityMap.put("辽宁盘锦", "辽宁-盘锦");
        cityMap.put("辽宁铁岭", "辽宁-铁岭");
        cityMap.put("辽宁朝阳", "辽宁-朝阳");
        cityMap.put("辽宁葫芦岛", "辽宁-葫芦岛");
        cityMap.put("吉林长春", "吉林-长春");
        cityMap.put("吉林吉林", "吉林-吉林");
        cityMap.put("吉林四平", "吉林-四平");
        cityMap.put("吉林辽源", "吉林-辽源");
        cityMap.put("吉林通化", "吉林-通化");
        cityMap.put("吉林白山", "吉林-白山");
        cityMap.put("吉林松原", "吉林-松原");
        cityMap.put("吉林白城", "吉林-白城");
        cityMap.put("吉林延边", "吉林-延边");
        cityMap.put("黑龙江哈尔滨", "黑龙江-哈尔滨");
        cityMap.put("黑龙江齐齐哈尔", "黑龙江-齐齐哈尔");
        cityMap.put("黑龙江鸡西", "黑龙江-鸡西");
        cityMap.put("黑龙江鹤岗", "黑龙江-鹤岗");
        cityMap.put("黑龙江双鸭山", "黑龙江-双鸭山");
        cityMap.put("黑龙江大庆", "黑龙江-大庆");
        cityMap.put("黑龙江伊春", "黑龙江-伊春");
        cityMap.put("黑龙江佳木斯", "黑龙江-佳木斯");
        cityMap.put("黑龙江七台河", "黑龙江-七台河");
        cityMap.put("黑龙江牡丹江", "黑龙江-牡丹江");
        cityMap.put("黑龙江黑河", "黑龙江-黑河");
        cityMap.put("黑龙江绥化", "黑龙江-绥化");
        cityMap.put("黑龙江大兴安岭", "黑龙江-大兴安岭");
        cityMap.put("江苏南京", "江苏-南京");
        cityMap.put("江苏无锡", "江苏-无锡");
        cityMap.put("江苏徐州", "江苏-徐州");
        cityMap.put("江苏常州", "江苏-常州");
        cityMap.put("江苏苏州", "江苏-苏州");
        cityMap.put("江苏南通", "江苏-南通");
        cityMap.put("江苏连云港", "江苏-连云港");
        cityMap.put("江苏淮安", "江苏-淮安");
        cityMap.put("江苏盐城", "江苏-盐城");
        cityMap.put("江苏扬州", "江苏-扬州");
        cityMap.put("江苏镇江", "江苏-镇江");
        cityMap.put("江苏泰州", "江苏-泰州");
        cityMap.put("江苏宿迁", "江苏-宿迁");
        cityMap.put("浙江杭州", "浙江-杭州");
        cityMap.put("浙江宁波", "浙江-宁波");
        cityMap.put("浙江温州", "浙江-温州");
        cityMap.put("浙江嘉兴", "浙江-嘉兴");
        cityMap.put("浙江湖州", "浙江-湖州");
        cityMap.put("浙江绍兴", "浙江-绍兴");
        cityMap.put("浙江金华", "浙江-金华");
        cityMap.put("浙江衢州", "浙江-衢州");
        cityMap.put("浙江舟山", "浙江-舟山");
        cityMap.put("浙江台州", "浙江-台州");
        cityMap.put("浙江丽水", "浙江-丽水");
        cityMap.put("安徽合肥", "安徽-合肥");
        cityMap.put("安徽芜湖", "安徽-芜湖");
        cityMap.put("安徽蚌埠", "安徽-蚌埠");
        cityMap.put("安徽淮南", "安徽-淮南");
        cityMap.put("安徽马鞍山", "安徽-马鞍山");
        cityMap.put("安徽淮北", "安徽-淮北");
        cityMap.put("安徽铜陵", "安徽-铜陵");
        cityMap.put("安徽安庆", "安徽-安庆");
        cityMap.put("安徽黄山", "安徽-黄山");
        cityMap.put("安徽滁州", "安徽-滁州");
        cityMap.put("安徽阜阳", "安徽-阜阳");
        cityMap.put("安徽宿州", "安徽-宿州");
        cityMap.put("安徽巢湖", "安徽-巢湖");
        cityMap.put("安徽六安", "安徽-六安");
        cityMap.put("安徽亳州", "安徽-亳州");
        cityMap.put("安徽池州", "安徽-池州");
        cityMap.put("安徽宣城", "安徽-宣城");
        cityMap.put("福建福州", "福建-福州");
        cityMap.put("福建厦门", "福建-厦门");
        cityMap.put("福建莆田", "福建-莆田");
        cityMap.put("福建三明", "福建-三明");
        cityMap.put("福建泉州", "福建-泉州");
        cityMap.put("福建漳州", "福建-漳州");
        cityMap.put("福建南平", "福建-南平");
        cityMap.put("福建龙岩", "福建-龙岩");
        cityMap.put("福建宁德", "福建-宁德");
        cityMap.put("江西南昌", "江西-南昌");
        cityMap.put("江西景德镇", "江西-景德镇");
        cityMap.put("江西萍乡", "江西-萍乡");
        cityMap.put("江西九江", "江西-九江");
        cityMap.put("江西新余", "江西-新余");
        cityMap.put("江西鹰潭", "江西-鹰潭");
        cityMap.put("江西赣州", "江西-赣州");
        cityMap.put("江西吉安", "江西-吉安");
        cityMap.put("江西宜春", "江西-宜春");
        cityMap.put("江西抚州", "江西-抚州");
        cityMap.put("江西上饶", "江西-上饶");
        cityMap.put("山东济南", "山东-济南");
        cityMap.put("山东青岛", "山东-青岛");
        cityMap.put("山东淄博", "山东-淄博");
        cityMap.put("山东枣庄", "山东-枣庄");
        cityMap.put("山东东营", "山东-东营");
        cityMap.put("山东烟台", "山东-烟台");
        cityMap.put("山东潍坊", "山东-潍坊");
        cityMap.put("山东济宁", "山东-济宁");
        cityMap.put("山东泰安", "山东-泰安");
        cityMap.put("山东威海", "山东-威海");
        cityMap.put("山东日照", "山东-日照");
        cityMap.put("山东莱芜", "山东-莱芜");
        cityMap.put("山东临沂", "山东-临沂");
        cityMap.put("山东德州", "山东-德州");
        cityMap.put("山东聊城", "山东-聊城");
        cityMap.put("山东滨州", "山东-滨州");
        cityMap.put("山东荷泽", "山东-荷泽");
        cityMap.put("河南郑州", "河南-郑州");
        cityMap.put("河南开封", "河南-开封");
        cityMap.put("河南洛阳", "河南-洛阳");
        cityMap.put("河南平顶山", "河南-平顶山");
        cityMap.put("河南安阳", "河南-安阳");
        cityMap.put("河南鹤壁", "河南-鹤壁");
        cityMap.put("河南新乡", "河南-新乡");
        cityMap.put("河南焦作", "河南-焦作");
        cityMap.put("河南濮阳", "河南-濮阳");
        cityMap.put("河南许昌", "河南-许昌");
        cityMap.put("河南漯河", "河南-漯河");
        cityMap.put("河南三门峡", "河南-三门峡");
        cityMap.put("河南南阳", "河南-南阳");
        cityMap.put("河南商丘", "河南-商丘");
        cityMap.put("河南信阳", "河南-信阳");
        cityMap.put("河南周口", "河南-周口");
        cityMap.put("河南驻马店", "河南-驻马店");
        cityMap.put("湖北武汉", "湖北-武汉");
        cityMap.put("湖北黄石", "湖北-黄石");
        cityMap.put("湖北十堰", "湖北-十堰");
        cityMap.put("湖北宜昌", "湖北-宜昌");
        cityMap.put("湖北襄樊", "湖北-襄樊");
        cityMap.put("湖北鄂州", "湖北-鄂州");
        cityMap.put("湖北荆门", "湖北-荆门");
        cityMap.put("湖北孝感", "湖北-孝感");
        cityMap.put("湖北荆州", "湖北-荆州");
        cityMap.put("湖北黄冈", "湖北-黄冈");
        cityMap.put("湖北咸宁", "湖北-咸宁");
        cityMap.put("湖北随州", "湖北-随州");
        cityMap.put("湖北恩施", "湖北-恩施");
        cityMap.put("湖北仙桃", "湖北-仙桃");
        cityMap.put("湖北潜江", "湖北-潜江");
        cityMap.put("湖北天门", "湖北-天门");
        cityMap.put("湖北神农架", "湖北-神农架");
        cityMap.put("湖南长沙", "湖南-长沙");
        cityMap.put("湖南株洲", "湖南-株洲");
        cityMap.put("湖南湘潭", "湖南-湘潭");
        cityMap.put("湖南衡阳", "湖南-衡阳");
        cityMap.put("湖南邵阳", "湖南-邵阳");
        cityMap.put("湖南岳阳", "湖南-岳阳");
        cityMap.put("湖南常德", "湖南-常德");
        cityMap.put("湖南张家界", "湖南-张家界");
        cityMap.put("湖南益阳", "湖南-益阳");
        cityMap.put("湖南郴州", "湖南-郴州");
        cityMap.put("湖南永州", "湖南-永州");
        cityMap.put("湖南怀化", "湖南-怀化");
        cityMap.put("湖南娄底", "湖南-娄底");
        cityMap.put("湖南湘西", "湖南-湘西");
        cityMap.put("广东广州", "广东-广州");
        cityMap.put("广东韶关", "广东-韶关");
        cityMap.put("广东深圳", "广东-深圳");
        cityMap.put("广东珠海", "广东-珠海");
        cityMap.put("广东汕头", "广东-汕头");
        cityMap.put("广东佛山", "广东-佛山");
        cityMap.put("广东江门", "广东-江门");
        cityMap.put("广东湛江", "广东-湛江");
        cityMap.put("广东茂名", "广东-茂名");
        cityMap.put("广东肇庆", "广东-肇庆");
        cityMap.put("广东惠州", "广东-惠州");
        cityMap.put("广东梅州", "广东-梅州");
        cityMap.put("广东汕尾", "广东-汕尾");
        cityMap.put("广东河源", "广东-河源");
        cityMap.put("广东阳江", "广东-阳江");
        cityMap.put("广东清远", "广东-清远");
        cityMap.put("广东东莞", "广东-东莞");
        cityMap.put("广东中山", "广东-中山");
        cityMap.put("广东潮州", "广东-潮州");
        cityMap.put("广东揭阳", "广东-揭阳");
        cityMap.put("广东云浮", "广东-云浮");
        cityMap.put("广西南宁", "广西-南宁");
        cityMap.put("广西柳州", "广西-柳州");
        cityMap.put("广西桂林", "广西-桂林");
        cityMap.put("广西梧州", "广西-梧州");
        cityMap.put("广西北海", "广西-北海");
        cityMap.put("广西防城港", "广西-防城港");
        cityMap.put("广西钦州", "广西-钦州");
        cityMap.put("广西贵港", "广西-贵港");
        cityMap.put("广西玉林", "广西-玉林");
        cityMap.put("广西百色", "广西-百色");
        cityMap.put("广西贺州", "广西-贺州");
        cityMap.put("广西河池", "广西-河池");
        cityMap.put("广西来宾", "广西-来宾");
        cityMap.put("广西崇左", "广西-崇左");
        cityMap.put("海南海口", "海南-海口");
        cityMap.put("海南三亚", "海南-三亚");
        cityMap.put("海南五指山", "海南-五指山");
        cityMap.put("海南琼海", "海南-琼海");
        cityMap.put("海南儋州", "海南-儋州");
        cityMap.put("海南文昌", "海南-文昌");
        cityMap.put("海南万宁", "海南-万宁");
        cityMap.put("海南东方", "海南-东方");
        cityMap.put("海南定安", "海南-定安");
        cityMap.put("海南屯昌", "海南-屯昌");
        cityMap.put("海南澄迈", "海南-澄迈");
        cityMap.put("海南临高", "海南-临高");
        cityMap.put("海南白沙", "海南-白沙");
        cityMap.put("海南昌江", "海南-昌江");
        cityMap.put("海南乐东", "海南-乐东");
        cityMap.put("海南陵水", "海南-陵水");
        cityMap.put("海南保亭", "海南-保亭");
        cityMap.put("海南琼中", "海南-琼中");
        cityMap.put("海南西沙群岛", "海南-西沙群岛");
        cityMap.put("海南南沙群岛", "海南-南沙群岛");
        cityMap.put("海南中沙群岛", "海南-中沙群岛");
        cityMap.put("四川成都", "四川-成都");
        cityMap.put("四川自贡", "四川-自贡");
        cityMap.put("四川攀枝花", "四川-攀枝花");
        cityMap.put("四川泸州", "四川-泸州");
        cityMap.put("四川德阳", "四川-德阳");
        cityMap.put("四川绵阳", "四川-绵阳");
        cityMap.put("四川广元", "四川-广元");
        cityMap.put("四川遂宁", "四川-遂宁");
        cityMap.put("四川内江", "四川-内江");
        cityMap.put("四川乐山", "四川-乐山");
        cityMap.put("四川南充", "四川-南充");
        cityMap.put("四川眉山", "四川-眉山");
        cityMap.put("四川宜宾", "四川-宜宾");
        cityMap.put("四川广安", "四川-广安");
        cityMap.put("四川达州", "四川-达州");
        cityMap.put("四川雅安", "四川-雅安");
        cityMap.put("四川巴中", "四川-巴中");
        cityMap.put("四川资阳", "四川-资阳");
        cityMap.put("四川阿坝", "四川-阿坝");
        cityMap.put("四川甘孜", "四川-甘孜");
        cityMap.put("四川凉山", "四川-凉山");
        cityMap.put("贵州贵阳", "贵州-贵阳");
        cityMap.put("贵州六盘水", "贵州-六盘水");
        cityMap.put("贵州遵义", "贵州-遵义");
        cityMap.put("贵州安顺", "贵州-安顺");
        cityMap.put("贵州铜仁", "贵州-铜仁");
        cityMap.put("贵州黔西南", "贵州-黔西南");
        cityMap.put("贵州毕节", "贵州-毕节");
        cityMap.put("贵州黔东南", "贵州-黔东南");
        cityMap.put("贵州黔南", "贵州-黔南");
        cityMap.put("云南昆明", "云南-昆明");
        cityMap.put("云南曲靖", "云南-曲靖");
        cityMap.put("云南玉溪", "云南-玉溪");
        cityMap.put("云南保山", "云南-保山");
        cityMap.put("云南昭通", "云南-昭通");
        cityMap.put("云南丽江", "云南-丽江");
        cityMap.put("云南思茅", "云南-思茅");
        cityMap.put("云南临沧", "云南-临沧");
        cityMap.put("云南楚雄", "云南-楚雄");
        cityMap.put("云南红河", "云南-红河");
        cityMap.put("云南文山", "云南-文山");
        cityMap.put("云南西双版纳", "云南-西双版纳");
        cityMap.put("云南大理", "云南-大理");
        cityMap.put("云南德宏", "云南-德宏");
        cityMap.put("云南怒江", "云南-怒江");
        cityMap.put("云南迪庆", "云南-迪庆");
        cityMap.put("西藏拉萨", "西藏-拉萨");
        cityMap.put("西藏昌都", "西藏-昌都");
        cityMap.put("西藏山南", "西藏-山南");
        cityMap.put("西藏日喀则", "西藏-日喀则");
        cityMap.put("西藏那曲", "西藏-那曲");
        cityMap.put("西藏阿里", "西藏-阿里");
        cityMap.put("西藏林芝", "西藏-林芝");
        cityMap.put("陕西西安", "陕西-西安");
        cityMap.put("陕西铜川", "陕西-铜川");
        cityMap.put("陕西宝鸡", "陕西-宝鸡");
        cityMap.put("陕西咸阳", "陕西-咸阳");
        cityMap.put("陕西渭南", "陕西-渭南");
        cityMap.put("陕西延安", "陕西-延安");
        cityMap.put("陕西汉中", "陕西-汉中");
        cityMap.put("陕西榆林", "陕西-榆林");
        cityMap.put("陕西安康", "陕西-安康");
        cityMap.put("陕西商洛", "陕西-商洛");
        cityMap.put("甘肃兰州", "甘肃-兰州");
        cityMap.put("甘肃嘉峪关", "甘肃-嘉峪关");
        cityMap.put("甘肃金昌", "甘肃-金昌");
        cityMap.put("甘肃白银", "甘肃-白银");
        cityMap.put("甘肃天水", "甘肃-天水");
        cityMap.put("甘肃武威", "甘肃-武威");
        cityMap.put("甘肃张掖", "甘肃-张掖");
        cityMap.put("甘肃平凉", "甘肃-平凉");
        cityMap.put("甘肃酒泉", "甘肃-酒泉");
        cityMap.put("甘肃庆阳", "甘肃-庆阳");
        cityMap.put("甘肃定西", "甘肃-定西");
        cityMap.put("甘肃陇南", "甘肃-陇南");
        cityMap.put("甘肃临夏", "甘肃-临夏");
        cityMap.put("甘肃甘州", "甘肃-甘州");
        cityMap.put("青海西宁", "青海-西宁");
        cityMap.put("青海海东", "青海-海东");
        cityMap.put("青海海州", "青海-海州");
        cityMap.put("青海黄南", "青海-黄南");
        cityMap.put("青海海南", "青海-海南");
        cityMap.put("青海果洛", "青海-果洛");
        cityMap.put("青海玉树", "青海-玉树");
        cityMap.put("青海海西", "青海-海西");
        cityMap.put("宁夏银川", "宁夏-银川");
        cityMap.put("宁夏石嘴山", "宁夏-石嘴山");
        cityMap.put("宁夏吴忠", "宁夏-吴忠");
        cityMap.put("宁夏固原", "宁夏-固原");
        cityMap.put("宁夏中卫", "宁夏-中卫");
        cityMap.put("新疆乌鲁木齐", "新疆-乌鲁木齐");
        cityMap.put("新疆克拉玛依", "新疆-克拉玛依");
        cityMap.put("新疆吐鲁番", "新疆-吐鲁番");
        cityMap.put("新疆哈密", "新疆-哈密");
        cityMap.put("新疆昌吉", "新疆-昌吉");
        cityMap.put("新疆博尔", "新疆-博尔");
        cityMap.put("新疆巴音郭楞", "新疆-巴音郭楞");
        cityMap.put("新疆阿克苏", "新疆-阿克苏");
        cityMap.put("新疆克孜勒苏柯尔克孜", "新疆-克孜勒苏柯尔克孜");
        cityMap.put("新疆喀什", "新疆-喀什");
        cityMap.put("新疆和田", "新疆-和田");
        cityMap.put("新疆伊犁", "新疆-伊犁");
        cityMap.put("新疆塔城", "新疆-塔城");
        cityMap.put("新疆阿勒泰", "新疆-阿勒泰");
        cityMap.put("新疆石河子", "新疆-石河子");
        cityMap.put("新疆阿拉尔", "新疆-阿拉尔");
        cityMap.put("新疆图木舒克", "新疆-图木舒克");
        cityMap.put("新疆五家渠", "新疆-五家渠");
        cityMap.put("台湾台北", "台湾-台北");
        cityMap.put("台湾高雄", "台湾-高雄");
        cityMap.put("台湾基隆", "台湾-基隆");
        cityMap.put("台湾新竹", "台湾-新竹");
        cityMap.put("台湾台中", "台湾-台中");
        cityMap.put("台湾嘉义", "台湾-嘉义");
        cityMap.put("台湾台南", "台湾-台南");
        cityMap.put("台湾桃园", "台湾-桃园");
        cityMap.put("台湾苗栗", "台湾-苗栗");
        cityMap.put("台湾彰化", "台湾-彰化");
        cityMap.put("台湾南投", "台湾-南投");
        cityMap.put("台湾云林", "台湾-云林");
        cityMap.put("台湾屏东", "台湾-屏东");
        cityMap.put("台湾宜兰", "台湾-宜兰");
        cityMap.put("台湾花莲", "台湾-花莲");
        cityMap.put("台湾台东", "台湾-台东");
        cityMap.put("台湾澎湖", "台湾-澎湖");
        cityMap.put("台湾金门", "台湾-金门");
        cityMap.put("台湾连江", "台湾-连江");
        cityMap.put("香港香港", "香港");
        cityMap.put("澳门澳门", "澳门");
        cityMap.put("香港中西区", "香港");
        cityMap.put("香港东区", "香港");
        cityMap.put("香港南区", "香港");
        cityMap.put("香港湾仔区", "香港");
        cityMap.put("香港九龙城区", "香港");
        cityMap.put("香港观塘区", "香港");
        cityMap.put("香港深水埗区", "香港");
        cityMap.put("香港黄大仙区", "香港");
        cityMap.put("香港油尖旺区", "香港");
        cityMap.put("香港离岛区", "香港");
        cityMap.put("香港葵青区", "香港");
        cityMap.put("香港北区", "香港");
        cityMap.put("香港西贡区", "香港");
        cityMap.put("香港沙田区", "香港");
        cityMap.put("香港大埔区", "香港");
        cityMap.put("香港荃湾区", "香港");
        cityMap.put("香港屯门区", "香港");
        cityMap.put("香港元朗区", "香港");
        cityMap.put("澳门花地玛堂区", "澳门");
        cityMap.put("澳门市圣安多尼堂区", "澳门");
        cityMap.put("澳门大堂区", "澳门");
        cityMap.put("澳门望德堂区", "澳门");
        cityMap.put("澳门风顺堂区", "澳门");
        cityMap.put("澳门嘉模堂区", "澳门");
        cityMap.put("澳门圣方济各堂区", "澳门");
        cityMap.put("钓鱼岛", "钓鱼岛");
        // nickname
        cityMap.put("黑龙江哈市", "黑龙江-哈尔滨");
        cityMap.put("内蒙古呼市", "内蒙古-呼和浩特");
        cityMap.put("新疆巴州", "新疆-巴音郭楞");
    }

}
