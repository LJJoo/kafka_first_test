import axios from 'axios';

// 로컬 개발: vite.config.js proxy가 /api/producer → 8080, /api/consumer → 8081 로 라우팅
// Docker:   nginx가 동일한 역할 수행 (nginx.conf 참고)
const producer = axios.create({ baseURL: '/api/producer' });
const consumer = axios.create({ baseURL: '/api/consumer' });

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
