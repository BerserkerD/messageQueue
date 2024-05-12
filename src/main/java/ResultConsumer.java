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

public class ResultConsumer {
    private static String brokerURL = "tcp://localhost:61616";
    private static ConnectionFactory factory;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private Topic resultTopic;
    private Map<Integer, JFrame> frameMap;
    private Map<Integer, XYSeries> meanMap;
    private Map<Integer, XYSeries> varianceMap;
    private Map<Integer, XYSeries> maxMap;
    private Map<Integer, XYSeries> minMap;

    public ResultConsumer() throws JMSException {
        factory = new ActiveMQConnectionFactory(brokerURL);
        connection = factory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();

        frameMap = new HashMap<>();
        meanMap = new HashMap<>();
        varianceMap = new HashMap<>();
        maxMap = new HashMap<>();
        minMap = new HashMap<>();

        resultTopic = session.createTopic("Result");
        consumer = session.createConsumer(resultTopic);
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                try {
                    String analysisResult = ((TextMessage)message).getText();
                    processResult(analysisResult);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void processResult(String result) {
        // 假设 analysisResult 是要解析的字符串
        String[] parts = result.split(" "); // 使用空格分割字符串
        int deviceID = Integer.parseInt(parts[0]); // 提取第一个部分作为设备ID
        double mean = Double.parseDouble(parts[1]); // 提取第二个部分作为均值
        double variance = Double.parseDouble(parts[2]); // 提取第三个部分作为方差
        double max = Double.parseDouble(parts[3]); // 提取第四个部分作为最大值
        double min = Double.parseDouble(parts[4]); // 提取第五个部分作为最小值

        // 更新数据集
        if (!meanMap.containsKey(deviceID)) {
            meanMap.put(deviceID, new XYSeries("mean"));
            varianceMap.put(deviceID, new XYSeries("variance"));
            maxMap.put(deviceID, new XYSeries("max"));
            minMap.put(deviceID, new XYSeries("min"));
            JFrame frame = createFrame("Device " + deviceID);
            frameMap.put(deviceID, frame);
        }
        XYSeries means = meanMap.get(deviceID);
        means.add(means.getItemCount(), mean);
        XYSeries variances = meanMap.get(deviceID);
        variances.add(variances.getItemCount(), variance);
        XYSeries maxs = meanMap.get(deviceID);
        maxs.add(maxs.getItemCount(), max);
        XYSeries mins = meanMap.get(deviceID);
        mins.add(mins.getItemCount(), min);

        //更新图表
        updateChart(deviceID);

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
        dataset.addSeries(meanMap.get(deviceID));
        dataset.addSeries(varianceMap.get(deviceID));
        dataset.addSeries(maxMap.get(deviceID));
        dataset.addSeries(minMap.get(deviceID));

        // 使用 ChartFactory 创建一个 XY 折线图，并设置标题、X轴标签和Y轴标签
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Device " + deviceID + " Total Analysis", "Data Point Index", "Value", dataset);

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

    public static void main(String[] args) {
        try {
            ResultConsumer consumer = new ResultConsumer();
            System.out.println("数据统计结果显示服务启动，正在等待数据到来...");
            System.in.read(); // 暂停程序，直到收到消息
            consumer.close();
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

