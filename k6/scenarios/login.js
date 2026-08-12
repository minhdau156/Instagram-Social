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
        'http_req_duration{scenario:login}': ['p(95)<500'],
        'http_req_failed': ['rate<0.01']
    }
}

export function setup() {
    return {token : registerAndLogin("loadtester", "k6@k6.com", "loadtester")};
}



export default function () {
    const res = http.post(`${BASE_URL}/api/v1/auth/login`,
         JSON.stringify({ identifier: "loadtester", password: "loadtester" }), 
         { headers: { "Content-Type": "application/json" }, 
         tags: { scenario: "login" } },
        );
    
    check(res, {
        'login is 200': (r) => r.status === 200,
        'token returned': (r) => {
            try {
                return !!JSON.parse(r.body).data.accessToken;
            } catch {
                return false;
            }
        },
    })
    
    sleep(1);
}