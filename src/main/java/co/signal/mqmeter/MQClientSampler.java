/*
 * Copyright 2019 JoseLuisSR
 *
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 */
package co.signal.mqmeter;

import com.ibm.mq.*;
import com.ibm.mq.constants.MQConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;

/**
 * This class is to publish message on WebSphere MQ topic.
 * @author JoseLuisSR
 * @since 01/13/2019
 * @see "https://github.com/JoseLuisSR/mqmeter"
 */
public class MQClientSampler extends AbstractJavaSamplerClient {

    private static final Logger log = LoggingManager.getLoggerForClass();

    /**
     *  Parameter for setting the MQ Manager.
     */
    private static final String PARAMETER_MQ_MANAGER = "mq_manager";

    /**
     * Parameter for setting MQ QUEUE to put message, could be LOCAL or REMOTE.
     */
    private static final String PARAMETER_MQ_QUEUE_RQST = "mq_queue_rqst";

    /**
     * Parameter for setting MQ QUEUE for get response message, could be LOCAL or REMOTE.
     */
    private static final String PARAMETER_MQ_QUEUE_RSPS = "mq_queue_rsps";

    /**
     * Parameter for setting correlate response message with request message.
     */
    private static final String PARAMETER_MQ_CORRELATE_RSPS_MSG = "mq_correlate_rsps_msg";

    /**
     * Constant to correlate response message with messageID.
     */
    private static final String MESSAGE_ID = "messageId";

    /**
     * Constant to correlate response message with correlationID.
     */
    private static final String CORRELATION_ID = "correlationId";

    /**
     * Parameter for setting MQ Hostname where MQ Server is deploying.
     */
    private static final String PARAMETER_MQ_HOSTNAME = "mq_hostname";

    /**
     * Parameter for setting MQ Channel, it should be server connection channel.
     */
    private static final String PARAMETER_MQ_CHANNEL = "mq_channel";

    /**
     * Parameter for setting MQ USER ID.
     */
    private static final String PARAMETER_MQ_USER_ID = "mq_user_id";

    /**
     * Parameter for setting MQ User password.
     */
    private static final String PARAMETER_MQ_USER_PASSWORD = "mq_user_password";

    /**
     * Parameter for setting MQ PORT, is the Listener port.
     */
    private static final String PARAMETER_MQ_PORT = "mq_port";

    /**
     * Parameter for setting MQ Message.
     */
    private static final String PARAMETER_MQ_MESSAGE = "mq_message";

    /**
     * Parameter for setting MQ Encoding Message.
     */
    private static final String PARAMETER_MQ_ENCODING_MESSAGE = "mq_encoding_message";

    /**
     * Parameter to set wait interval to get message on queue.
     */
    private static final String PARAMETER_MQ_WAIT_INTERVAL = "mq_wait_interval";

    /**
     * Parameter for encoding.
     */
    private static final String ENCODING = "UTF-8";

    /**
     * MQTopic variable.
     */
    private MQQueueManager mqMgr;

    /**
     * Properties variable.
     */
    private Hashtable properties;

    /**
     * Initial values for test parameter. They are show in Java Request test sampler.
     * @return Arguments to set as default.
     */
    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameter = new Arguments();
        defaultParameter.addArgument(PARAMETER_MQ_MANAGER, "${MQ_MANAGER}");
        defaultParameter.addArgument(PARAMETER_MQ_QUEUE_RQST, "${MQ_QUEUE_RQST}");
        defaultParameter.addArgument(PARAMETER_MQ_QUEUE_RSPS, "");
        defaultParameter.addArgument(PARAMETER_MQ_CORRELATE_RSPS_MSG, "");
        defaultParameter.addArgument(PARAMETER_MQ_WAIT_INTERVAL, "");
        defaultParameter.addArgument(PARAMETER_MQ_HOSTNAME, "${MQ_HOSTNAME}");
        defaultParameter.addArgument(PARAMETER_MQ_PORT, "${MQ_PORT}");
        defaultParameter.addArgument(PARAMETER_MQ_CHANNEL, "${MQ_CHANNEL}");
        defaultParameter.addArgument(PARAMETER_MQ_USER_ID, "");
        defaultParameter.addArgument(PARAMETER_MQ_USER_PASSWORD,"");
        defaultParameter.addArgument(PARAMETER_MQ_ENCODING_MESSAGE, "${MQ_ENCODING_MESSAGE}");
        defaultParameter.addArgument(PARAMETER_MQ_MESSAGE, "${MQ_MESSAGE}");
        return defaultParameter;
    }

    /**
     * Read the test parameter and initialize your test client.
     * @param context to get the arguments values on Java Sampler.
     */
    @Override
    public void setupTest(JavaSamplerContext context) {

        // SET MQ Manager properties to connection.
        properties = new Hashtable<String, Object>();
        properties.put(MQConstants.HOST_NAME_PROPERTY, context.getParameter(PARAMETER_MQ_HOSTNAME));
        properties.put(MQConstants.PORT_PROPERTY, Integer.parseInt(context.getParameter(PARAMETER_MQ_PORT)));
        properties.put(MQConstants.CHANNEL_PROPERTY, context.getParameter(PARAMETER_MQ_CHANNEL));
        properties.put(MQConstants.USE_MQCSP_AUTHENTICATION_PROPERTY, true);
        String userID = context.getParameter(PARAMETER_MQ_USER_ID);

        if( userID != null && !userID.isEmpty())
            properties.put(MQConstants.USER_ID_PROPERTY, userID);
        String password = context.getParameter(PARAMETER_MQ_USER_PASSWORD);

        if( password != null && !password.isEmpty() )
            properties.put(MQConstants.PASSWORD_PROPERTY, password);

        log.info("MQ Manager properties are hostname: " + properties.get(MQConstants.HOST_NAME_PROPERTY) + " port: " +
                properties.get(MQConstants.PORT_PROPERTY) + " channel: " + properties.get(MQConstants.CHANNEL_PROPERTY));
    }

    /**
     * Close and disconnect MQ variables.
     * @param context to get the arguments values on Java Sampler.
     */
    @Override
    public void teardownTest(JavaSamplerContext context) {
        if( mqMgr != null && mqMgr.isConnected() ) {
            try {
                log.info("Disconnecting from the Queue Manager");
                mqMgr.disconnect();
                log.info("Done!");
            } catch (MQException e) {
                log.info("teardownTest " + e.getCause());
            }
        }
    }

    /**
     * Main method to execute the test on single thread.
     * @param context to get the arguments values on Java Sampler.
     * @return SampleResult, captures data such as whether the test was successful,
     * the response code and message, any request or response data, and the test start/end times
     */
    @Override
    public SampleResult runTest(JavaSamplerContext context) {

        SampleResult result = newSampleResult();
        String message = context.getParameter(PARAMETER_MQ_MESSAGE);
        String mq_Manager = context.getParameter(PARAMETER_MQ_MANAGER);
        String response;
        byte[] messageId;
        sampleResultStart(result, message);

        try{
            //Connecting to MQ Manager.
            log.info("Connecting to queue manager " + mq_Manager);
            mqMgr = new MQQueueManager(mq_Manager, properties);
            //Put message on queue.
            messageId= putMQMessage(context, message);
            //Get message on queue.
            response = getMQMessage(context, messageId);
            sampleResultSuccess(result, response);
        }catch (MQException e){
            sampleResultFail(result, "500", e);
            log.info("runTest " + e.getMessage() + " " + MQConstants.lookupReasonCode(e.getReason()) );
        } catch (Exception e) {
            sampleResultFail(result, "500", e);
            log.info("runTest " + e.getMessage());
        }
        return result;
    }


    /**
     * Method to open a mq queue, put message and close mq queue.
     * @param context to get the arguments values on Java Sampler.
     * @param message to put on mq queue.
     * @return messageId generate by MQ Manager.
     * @throws Exception
     */
    private byte[] putMQMessage(JavaSamplerContext context, String message) throws MQException, IOException {
        String mq_Queue = context.getParameter(PARAMETER_MQ_QUEUE_RQST);
        String encodingMsg = context.getParameter(PARAMETER_MQ_ENCODING_MESSAGE);
        MQMessage mqMessage = new MQMessage();
        MQQueue mqQueue;

        log.info("Accessing queue: " + mq_Queue);
        mqQueue = mqMgr.accessQueue(mq_Queue, MQConstants.MQOO_OUTPUT);
        log.info("Sending a message...");
        mqMessage.write(message.getBytes(encodingMsg));
        mqQueue.put(mqMessage, new MQPutMessageOptions());
        log.info("Closing the queue");
        mqQueue.close();
        return mqMessage.messageId;
    }

    /**
     * Method to open mq queue, get message and close mq queue.
     * @param context to get the arguments values on Java Sampler.
     * @param messageId to correlate response message with request message.
     * @return String, message on mq queue.
     * @throws Exception
     */
    private String getMQMessage(JavaSamplerContext context, byte[] messageId) throws MQException, UnsupportedEncodingException {
        String mq_Queue = context.getParameter(PARAMETER_MQ_QUEUE_RSPS);
        MQGetMessageOptions mqGMO = new MQGetMessageOptions();
        String response = null;

        if( mq_Queue != null && !mq_Queue.isEmpty()){
            String encodingMsg = context.getParameter(PARAMETER_MQ_ENCODING_MESSAGE);
            String correlateRspMssg = context.getParameter(PARAMETER_MQ_CORRELATE_RSPS_MSG);
            MQMsg2 mqMsg2 = new MQMsg2();
            MQQueue mqQueue;

            log.info("Accessing queue: " + mq_Queue);
            mqQueue = mqMgr.accessQueue(mq_Queue, MQConstants.MQOO_INPUT_AS_Q_DEF);

            // Set message id from request message to get response message
            if(correlateRspMssg == null || correlateRspMssg.isEmpty())
                mqMsg2.setMessageId(messageId);
            else if(correlateRspMssg.equals(MESSAGE_ID))
                mqMsg2.setMessageId(messageId);
            else if(correlateRspMssg.equals(CORRELATION_ID))
                mqMsg2.setCorrelationId(messageId);

            //Set wait Interval to get message on queue.
            String waitInterval = context.getParameter(PARAMETER_MQ_WAIT_INTERVAL);
            if(waitInterval != null && !waitInterval.isEmpty() && StringUtils.isNumeric(waitInterval)) {
                mqGMO.options = MQConstants.MQGMO_WAIT;
                mqGMO.waitInterval = Integer.parseInt(waitInterval);
            }

            log.info("Getting a message...");
            mqQueue.getMsg2(mqMsg2, mqGMO);
            response = new String(mqMsg2.getMessageData(),encodingMsg);
            log.info("Closing the queue");
            mqQueue.close();
        }

        return response;
    }

    /**
     *
     * @return SampleResult, captures data such as whether the test was successful,
     * the response code and message, any request or response data, and the test start/end times
     */
    private SampleResult newSampleResult(){
        SampleResult result = new SampleResult();
        result.setDataEncoding(ENCODING);
        result.setDataType(SampleResult.TEXT);
        return result;
    }

    /**
     * Start the sample request and set the <code>samplerData</code> to the
     * requestData.
     *
     * @param result
     *          the sample result to update
     * @param data
     *          the request to set as <code>samplerData</code>
     */
    private void sampleResultStart(SampleResult result, String data){
        result.setSamplerData(data);
        result.sampleStart();
    }

    /**
     * Set the sample result as <code>sampleEnd()</code>,
     * <code>setSuccessful(true)</code>, <code>setResponseCode("OK")</code> and if
     * the response is not <code>null</code> then
     * <code>setResponseData(response.toString(), ENCODING)</code> otherwise it is
     * marked as not requiring a response.
     *
     * @param result
     *          sample result to change
     * @param response
     *          the successful result message, may be null.
     */
    private void sampleResultSuccess(SampleResult result, String response){
        result.sampleEnd();
        result.setSuccessful(true);
        result.setResponseCodeOK();
        if(response != null)
            result.setResponseData(response, ENCODING);
        else
            result.setResponseData("No response required", ENCODING);
    }

    /**
     * Mark the sample result as <code>sampleEnd</code>,
     * <code>setSuccessful(false)</code> and the <code>setResponseCode</code> to
     * reason.
     *
     * @param result
     *          the sample result to change
     * @param reason
     *          the failure reason
     */
    private void sampleResultFail(SampleResult result, String reason, Exception exception){
        result.sampleEnd();
        result.setSuccessful(false);
        result.setResponseCode(reason);
        String responseMessage;

        responseMessage = "Exception: " + exception.getMessage();
        responseMessage += exception.getClass().equals(MQException.class) ? " MQ Reason Code: " + MQConstants.lookupReasonCode(((MQException)exception).getReason()) : "";
        responseMessage += exception.getCause() != null ? " Cause: " + exception.getCause() : "";
        result.setResponseMessage(responseMessage);

        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        result.setResponseData(stringWriter.toString(), ENCODING);
    }

}