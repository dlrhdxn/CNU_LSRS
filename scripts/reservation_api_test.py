#!/usr/bin/env python3
from urllib.parse import urlparse
from datetime import datetime, timedelta,date
import requests


DEFAULT_BASE_URL = "http://localhost:8080"
head = {"Content-type": "application/json"}
# uid 4,5,6,7
# re id _ , 5,6,7
# 20240001 - 예약 X 
# 20240002 - 예약 O QR 발급 전 
# 20240003 - 예약 O QR 발급 후
# 20240004 - 예약 O 체크인 만료

def login(account):
    r = requests.session()
    r.post(DEFAULT_BASE_URL+'/api/auth/login',json={'loginId':account,'password':'Test1234!'},headers=head)
    # print(account, "로그인")
    return r

def reserve(r,seat_id):
    d = date.today().strftime("%Y-%m-%d")
    now = datetime.now()
    starteAt = (now + timedelta(minutes=1)).strftime("%H:%M")
    endAt = (now+timedelta(minutes=20)).strftime("%H:%M")
    return r.post(DEFAULT_BASE_URL+'/api/reservations',json={"seatId": seat_id, "date": d, "startAt": starteAt, "endAt": endAt},headers=head)
# 중복예약

def dup_res():
    r = login("20240001")
    print(reserve(r,5).json())
    print(reserve(r,6).json())
    print(reserve(r,7).json())

# 중복 체크인
def dup_check(seatCode,token):
    r = login("kiosk1")
    print(r.post(DEFAULT_BASE_URL+"/api/check-in",json={'seatCode' :seatCode,'qrToken':token},headers=head).json())

# t = "da4kWz1P6_6RSvIElUuNBj7QT8uUC5oN8Hz5rxbmE6k"
# dup_check('A-06',t)
# dup_check('A-06',t)

# QR 타입체킹

def qrType():
    r = login("20240003")
    print(r.get(DEFAULT_BASE_URL+'/reservations/6/qr').text)
    print(r.get(DEFAULT_BASE_URL+'/reservations/-1/qr').text)
    print(r.get(DEFAULT_BASE_URL+'/reservations/abcd/qr').text)

qrType()