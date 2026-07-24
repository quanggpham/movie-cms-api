FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    openssh-server \
    openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

# SSH setup
RUN mkdir /var/run/sshd
RUN useradd -m moviebot

# Key-only SSH auth
RUN sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
RUN sed -i 's/#PubkeyAuthentication yes/PubkeyAuthentication yes/' /etc/ssh/sshd_config

# App directories
RUN mkdir -p /opt/movie-crawler/data /opt/movie-crawler/backup /opt/movie-crawler/logs
RUN chown -R moviebot:moviebot /opt/movie-crawler

# Copy SSH authorized_keys
COPY authorized_keys /home/moviebot/.ssh/authorized_keys
RUN chmod 700 /home/moviebot/.ssh && \
    chmod 600 /home/moviebot/.ssh/authorized_keys && \
    chown -R moviebot:moviebot /home/moviebot/.ssh

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]
