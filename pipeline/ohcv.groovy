pipeline {
    // 环境变量
    environment {
        url = 'https://github.com/Alomerry/oh-cv.git' 
    }
    // pipeline 的触发方式
    triggers {
        GenericTrigger(
            genericVariables: [
                [
                   key: 'name', 
                   value: '$.repository.name', 
                   expressionType: 'JSONPath', 
                   regularFilter: '', 
                   defaultValue: ''
                ]
            ],
            printContributedVariables: false, 
            printPostContent: false, 
            tokenCredentialId: 'jenkins-git-webhook-token',
            regexpFilterText: '$name',
            regexpFilterExpression: '^(O|o)h-cv$',
            causeString: ' Triggered on $ref' ,
        )
    }
    
    // 代理
    agent {
        docker {
            image 'registry.cn-hangzhou.aliyuncs.com/alomerry/blog-build:latest'
            args '-v /etc/timezone:/etc/timezone:ro -v /etc/localtime:/etc/localtime:ro'
        }
    }
    // 阶段
    stages {
        stage('pull code') {
            steps {
                retry(3) {
                    // 拉取代码
                    git(url: env.url, branch: 'develop')
                }
            }
        }
        stage('install and build') {
            steps {
                retry(3) {
                    // 构建
                    sh 'pnpm install --no-frozen-lockfile && pnpm build:pkg && pnpm build'
                }
                
            }
        }
        stage('compress') {
            steps {
                // 压缩构建后的文件用于发布到服务器的 nginx 中
                retry(3) {
                    sh '''
                    cd /var/jenkins_home/workspace/ohcv/site/dist/
                    tar -zcf ohcv.tar.gz *
                    '''
                }
            }
        }
        stage('ssh') {
            steps {
                script {
                    def remote = [:]
                    remote.name = 'root'
                    remote.logLevel = 'FINEST'
                    remote.host = 'ohcv.alomerry.com'
                    remote.allowAnyHosts = true
                    withCredentials([usernamePassword(credentialsId: 'tencent-vps-admin', passwordVariable: 'password', usernameVariable: 'username')]) {
                        remote.user = "${username}"
                        remote.password = "${password}"
                    }
                    sshCommand remote: remote, command: '''#!/bin/bash
                        cd /www/wwwroot/ohcv.alomerry.com/
                        shopt -s extglob
                        rm -rf !(.htaccess|.user.ini|.well-known|favicon.ico|ohcv.tar.gz)
                        '''
                    sshPut remote: remote, from: '/var/jenkins_home/workspace/ohcv/site/dist/ohcv.tar.gz', into: '/www/wwwroot/ohcv.alomerry.com/'
                    sshCommand remote: remote, command: "cd /www/wwwroot/ohcv.alomerry.com && tar -xf ohcv.tar.gz"
                    sshRemove remote: remote, path: '/www/wwwroot/ohcv.alomerry.com/ohcv.tar.gz'
                }
            }
        }
    }
}