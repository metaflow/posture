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
    ser = serial.Serial('/dev/tty.POSTURE-SPPDev', 38400)
    while (1):
        ln = ser.readline().decode('utf-8')
        parts = ln.strip().split('\t')
        if len(parts) < 11:
            continue
        # print(parts)
        sensors[parts[0]] = parts
        elapsed = time.time() - last_time
        # print(elapsed)
        if elapsed > 5:
            record()

def record():
    global last_time
    global sensors
    print(sensors)
    last_time = time.time()
    return_value, image = camera.read()
    t = int(last_time)
    print(t)
    cv2.imwrite(f'{t}.jpg',image)

    with open(f'values.csv', mode='a') as f:
        w = csv.writer(f, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
        for k in sensors.keys():
            w.writerow([t] + sensors[k])
    cv2.imshow('image', image)
    cv2.waitKey(100)

while (True):
    try:
        start()
    except Exception as e:
        print(e)
        exit(1)
        # time.sleep(10)
        # pass

#
# while (1):
#     print(ser.readline())
#     s = time.time()
#     return_value,image = camera.read()
#     d = time.time() - s
#     print(f'{d}')
#     t = int(s)
#     cv2.imwrite(f'{t}.jpg',image)
    # time.sleep(0.1)




while True:

    # gray = cv2.cvtColor(image,cv2.COLOR_BGR2GRAY)
    #
    # if cv2.waitKey(1)& 0xFF == ord('s'):

    break
camera.release()
cv2.destroyAllWindows()
