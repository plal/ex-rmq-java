package fib_rpc;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class fib_rmq_client {

  private Connection connection;
  private Channel channel;
  private String requestQueueName = "rpc_server";

  public fib_rmq_client() throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    connection = factory.newConnection();
    channel = connection.createChannel();
  }

  public String call(String message) throws IOException, InterruptedException {
    final String corrId = UUID.randomUUID().toString();

    String replyQueueName = channel.queueDeclare().getQueue();
    AMQP.BasicProperties props = new AMQP.BasicProperties
            .Builder()
            .correlationId(corrId)
            .replyTo(replyQueueName)
            .build();

    channel.basicPublish("", requestQueueName, props, message.getBytes("UTF-8"));

    final BlockingQueue<String> response = new ArrayBlockingQueue<String>(1);

    String ctag = channel.basicConsume(replyQueueName, true, new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        if (properties.getCorrelationId().equals(corrId)) {
          response.offer(new String(body, "UTF-8"));
        }
      }
    });

    String result = response.take();
    channel.basicCancel(ctag);
    return result;
  }

  public void close() throws IOException {
    connection.close();
  }

  public static void main(String[] argv) {
    fib_rmq_client fibonacciRpc = null;
    String response = null;
    try {
      PrintWriter writer = new PrintWriter("output_15.csv", "UTF-8");
      fibonacciRpc = new fib_rmq_client();
      for (int i = 0; i < 15; i++) {
        //String i_str = Integer.toString(i);
        //System.out.println("Calculate Fibonacci number of " + i_str);
        long start = System.currentTimeMillis();
        response = fibonacciRpc.call("45");
        long end = System.currentTimeMillis();


        long time_elapsed = end - start;
        //System.out.println(time_elapsed);
        writer.println(time_elapsed+",");
        System.out.println("Fibonacci number: " + response);
      }
      writer.close();
    }
    catch  (IOException | TimeoutException | InterruptedException e) {
      e.printStackTrace();
    }
    finally {
      if (fibonacciRpc!= null) {
        try {
          fibonacciRpc.close();
        }
        catch (IOException _ignore) {}
      }
    }
  }
}