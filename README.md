# network-protocol
Create a new reliable protocol, consuming less space than TCP

### Steps
1. Build subnet
docker network create --subnet=172.18.0.0/16 nodenet

2. Build docker container
docker build -t javaapptest .

3. Start the receiver 
docker run -it -p 8081:8080 --cap-add=NET_ADMIN --net nodenet --ip 172.18.0.22 javaapptest 2 172.18.0.21 R highDef.JPG

R: receiver mode
highDef.JPG: fileName to store
172.18.0.21: Sender IP

4. Start the Sender
S: Sender mode
highDef.JPG: fileName to send
172.18.0.22: receiver IP

5. Transfer file from docker container to Rover storage
docker cp 0323f62c1e3e:/app/RoverStorage/Rover2/hd1.JPG ./RoverStorage/Rover2

container name: 0323f62c1e3e
container storage with file Name: /app/RoverStorage/Rover2/hd1.JPG 
System Storage: ./RoverStorage/Rover2
