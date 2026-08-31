pipeline {
    agent any

    environment {
        AWS_REGION = 'us-east-1'
        ECR_REPOSITORY_URI = '<YOUR_AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/clausis-backend'
    }

    stages {
        stage('Deploy') {
            steps {
                script {
                    def IMAGE_TAG = env.image_tag ?: 'latest'
                    echo "Image ${IMAGE_TAG} was successfully built and pushed to ECR by GitHub Actions."
                    echo "NOTE: Kubernetes deployment steps have been removed."
                    echo "Please configure deployment steps (e.g. Docker Compose via SSH) based on your new architecture."
                }
            }
        }
    }
    
    post {
        success {
            echo "Pipeline completed."
        }
    }
}
