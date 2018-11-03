FROM python:3.6

RUN apt update
RUN apt install -y python-opengl
Add ./requirements.txt /
RUN pip install -r requirements.txt
ADD ./__pycache__ /agent

EXPOSE 10080
ENV NAME Agent

# Run
WORKDIR /agent
ENTRYPOINT ["python"]
CMD ["run_my_agent.cpython-36.pyc"]
