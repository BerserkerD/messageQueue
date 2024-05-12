import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AnalysisConsumer {
    private static String brokerURL = "tcp://localhost:61616";
    private static ActiveMQConnectionFactory factory;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private Topic dataTopic;
    private Topic resultTopic;
    private MessageProducer producer;
    private Map<Integer, List<Double>> dataMap;
    private int N;
    private Map<Integer, XYSeries> seriesMap;
    private Map<Integer, JFrame> frameMap;

    public AnalysisConsumer(int N) throws JMSException {
        factory = new ActiveMQConnectionFactory(brokerURL);
        factory.setTrustAllPackages(true);
        connection = factory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();

        dataMap = new HashMap<>();
        seriesMap = new HashMap<>();
        frameMap = new HashMap<>();

        dataTopic = session.createTopic("DataPoint");
        consumer = session.createConsumer(dataTopic);
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                try {
                    String dataPoint = ((TextMessage)message).getText();
                    processMessage(dataPoint);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        resultTopic = session.createTopic("Result");
        producer = session.createProducer(resultTopic);

        this.N = N;
    }

    private void processMessage(String dataPoint) {

        // 假设 dataPoint 是已经定义并且赋值的字符串
        int spaceIndex = dataPoint.indexOf(" ");
        double value = Double.parseDouble(dataPoint.substring(0, spaceIndex));
        int deviceID = Integer.parseInt(dataPoint.substring(spaceIndex + 1));

        List<Double> dataList = dataMap.getOrDefault(deviceID, new ArrayList<>());
        dataList.add(value);
        dataMap.put(deviceID, dataList);

        // 更新数据集
        if (!seriesMap.containsKey(deviceID)) {
            seriesMap.put(deviceID, new XYSeries("Value"));
            JFrame frame = createFrame("Device " + deviceID);
            frameMap.put(deviceID, frame);
        }
        XYSeries series = seriesMap.get(deviceID);
        series.add(series.getItemCount(), value);

        //更新图表
        updateChart(deviceID);

        if(dataList.size() < N) return;

        // 计算均值、方差、最大值和最小值
        double mean = calculateMean(dataList);
        double variance = calculateVariance(dataList, mean);
        double max = Collections.max(dataList);
        double min = Collections.min(dataList);

        // 创建新的消息，包含分析结果
        String analysisResult = String.valueOf(deviceID) + " " + String.format("%.3f", mean)
                + " " + String.format("%.3f", variance) + " " + String.format("%.3f", max) + " " + String.format("%.3f", min);
        try {
            Message message = session.createTextMessage(analysisResult);
            producer.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private JFrame createFrame(String title) {
        // 创建一个新的 JFrame 对象，并设置标题为提供的 title
        JFrame frame = new JFrame(title);
        // 设置当用户关闭窗口时默认关闭操作为释放当前窗口占用的资源
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // 设置窗口的布局为网格布局，1 行 1 列
        frame.setLayout(new GridLayout(1, 1));
        //设置窗口像素大小
        frame.setSize(600, 450);
        // 将窗口设置为可见状态
        frame.setVisible(true);
        // 返回创建的 JFrame 对象
        return frame;
    }

    private void updateChart(int deviceID) {
        // 更新图表
        // 创建一个 XYSeriesCollection 对象，用于存储图表数据
        XYSeriesCollection dataset = new XYSeriesCollection();
        // 向数据集中添加设备对应的 XYSeries
        dataset.addSeries(seriesMap.get(deviceID));

        // 使用 ChartFactory 创建一个 XY 折线图，并设置标题、X轴标签和Y轴标签
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Device " + deviceID + " Value Statistics", "Data Point Index", "Value", dataset);

        // 创建一个 ChartPanel 对象，用于显示折线图
        ChartPanel chartPanel = new ChartPanel(chart);
        // 获取设备对应的 JFrame
        JFrame frame = frameMap.get(deviceID);
        // 设置 JFrame 的内容面板为折线图面板
        frame.setContentPane(chartPanel);
        // 重新验证 JFrame 的布局
        frame.revalidate();
        // 重绘 JFrame
        frame.repaint();
    }


    private double calculateMean(List<Double> dataList) {
        double sum = 0;
        ListIterator<Double> iterator = dataList.listIterator(dataList.size());
        for (int i = 0; i < N; i++) {
            sum += iterator.previous();
        }
        return sum / dataList.size();
    }

    private double calculateVariance(List<Double> dataList, double mean) {
        double sum = 0;
        ListIterator<Double> iterator = dataList.listIterator(dataList.size());
        for (int i = 0; i < N; i++) {
            sum += Math.pow(iterator.previous() - mean, 2);
        }
        return sum / dataList.size();
    }

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);
            System.out.println("请输入N的值：");
            int N = sc.nextInt();
            //构造消费者
            AnalysisConsumer consumer = new AnalysisConsumer(N);
            System.out.println("数据统计分析服务启动，正在等待数据到来...");
            System.in.read(); // 暂停程序，直到收到消息
            consumer.close();
            sc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (consumer != null) {
                consumer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
