import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export function registerAndLogin(username, email, password) {
  http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username, email, password, fullName: "Load Tester" }),
    { headers: { "Content-Type": "application/json" } },
  );

  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ identifier: username, password }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(res, {
    "login is 200": (r) => r.status === 200,
    "token returned": (r) => {
      try {
        return !!JSON.parse(r.body).data.accessToken;
      } catch {
        return false;
      }
    },
  });

  return JSON.parse(res.body).data.accessToken;;
}


