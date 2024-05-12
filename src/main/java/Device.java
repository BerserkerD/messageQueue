import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.xml.crypto.Data;
import java.util.Random;
import java.util.Scanner;

public class Device {
    private static String brokerURL = "tcp://localhost:61616";
    private static ConnectionFactory factory;
    private Connection connection;
    private Session session;
    private MessageProducer producer;
    private Topic topic;

    private int ID;
    private double MEAN;
    private double STANDARD_DEVIATION;

    public Device(int id, double mean, double standard) throws JMSException {

        factory = new ActiveMQConnectionFactory(brokerURL);
        connection = factory.createConnection();

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topic = session.createTopic("DataPoint");
        producer = session.createProducer(topic);

        connection.start();

        ID = id;
        MEAN = mean;
        STANDARD_DEVIATION = standard;

    }

    public void close() throws JMSException {
        if (connection != null) {
            connection.close();
        }
    }

    public static void main(String[] args) throws JMSException {
        try{
            Scanner sc = new Scanner(System.in);
            System.out.println("请输入唯一设备标识号：");
            int id = sc.nextInt();
            System.out.println("请输入模拟的采集数据的均值和方差：");
            double mean = sc.nextDouble();
            double standard = sc.nextDouble();

            Device device = new Device(id, mean, standard);

            device.collectData();
            System.out.println("所有数据采集完毕！");
            device.close();
            sc.close();
        }  catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double generateRandomNumber(double mean, double stdDev, Random random) {
        double u1 = random.nextDouble();
        double u2 = random.nextDouble();

        double randStdNormal = Math.sqrt(-2.0 * Math.log(u1)) * Math.sin(2.0 * Math.PI * u2);
        return mean + stdDev * randStdNormal;
    }

    public void collectData() throws JMSException, InterruptedException {
        try {
            Random random = new Random();
            // 均值和标准差
            double mean = MEAN;
            double stdDev = STANDARD_DEVIATION;
            // 生成1000个数据点
            int numberOfDataPoints = 1000;
            for (int i = 0; i < numberOfDataPoints; i++) {

                double data = generateRandomNumber(mean, stdDev, random);

                String dataPoint = String.format("%.3f", data) + " " + String.valueOf(ID);

                Message message = session.createTextMessage(dataPoint);
                producer.send(message);
                Thread.sleep(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
