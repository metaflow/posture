import serial
import time
import cv2
import csv

sensors = {}
last_time = time.time()
camera = cv2.VideoCapture(0)

def start():
    global sensors
    global last_time
   

def record():
    global last_time
    last_time = time.time()
    _, image = camera.read()
    t = int(last_time)
    print(t)
    cv2.imwrite(f'./img/{t}.jpg',image)
    cv2.imshow('image', image)
    cv2.waitKey(100)

while (True):
    time.sleep(10)
    record()

camera.release()
cv2.destroyAllWindows()
