import axios from 'axios';

const producer = axios.create({ baseURL: 'http://localhost:8080' });
const consumer = axios.create({ baseURL: 'http://localhost:8081' });

export const createOrder = (data) => producer.post('/api/order', data);
export const getProducerOrders = () => producer.get('/api/orders');
export const getConsumerOrders = () => consumer.get('/api/orders');

export const pauseProducer = () => producer.post('/api/producer/pause');
export const resumeProducer = () => producer.post('/api/producer/resume');
export const getProducerStatus = () => producer.get('/api/producer/status');

export const getProducerBuffer = () => producer.get('/api/producer/buffer');
export const clearProducerBuffer = () => producer.delete('/api/producer/buffer');

export const pauseConsumer = () => consumer.post('/api/consumer/pause');
export const resumeConsumer = () => consumer.post('/api/consumer/resume');
export const getConsumerStatus = () => consumer.get('/api/consumer/status');
