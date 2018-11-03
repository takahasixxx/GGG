FROM python:3.6

RUN apt update
RUN apt install -y python-opengl

ADD ./examples/docker-agent /agent

ADD . /pommerman
RUN cd /pommerman && pip install -r requirements.txt

EXPOSE 10080

ENV NAME Agent

# Run python run_ibm_agent.py
WORKDIR /agent
ENTRYPOINT ["python"]
CMD ["run_ibm_agent.py"]
