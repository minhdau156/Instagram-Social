import http from "k6/http";
import { check, sleep } from "k6";
import { registerAndLogin } from "../lib/auth.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
    stages: [
        {duration: '15s', target: 2},  //ramp up to 2 VUs
        {duration: '30s', target: 3},   //hold at 3 VUs
        {duration: '15s', target: 0}    //ramp down
    ],
    thresholds: {
        'http_req_duration{scenario:login}': ['p(95)<700'],
        'http_req_failed': ['rate<0.01']
    }
} 

export function setup() {
    return {token : registerAndLogin("loadtester", "k6@k6.com", "loadtester")};
}

export default function (data) {
    const res = http.get(`${BASE_URL}/api/v1/feed?limit=10`, { headers: { Authorization: `Bearer ${data.token}` } });
    check(res, { 'feed page 0 is 200': (r) => r.status === 200 });
    sleep(2);
}