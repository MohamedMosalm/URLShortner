pipeline {
  agent any
  environment { IMAGE = 'docker.io/mohamedmosalm/url-shortener' }
  stages {
    stage('Build and push') {
      when { not { changelog '.*\\[skip ci\\].*' } }
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'U', passwordVariable: 'P')]) {
          sh '''
            echo "$P" | docker login -u "$U" --password-stdin
            docker build -t $IMAGE:$BUILD_NUMBER .
            docker push $IMAGE:$BUILD_NUMBER
        '''
        }
      }
    }
    stage('Update manifest') {
      when { not { changelog '.*\\[skip ci\\].*' } }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'U', passwordVariable: 'P')]) {
          sh '''
            sed -i "s#image: .*#image: $IMAGE:$BUILD_NUMBER#" k8s/app.yaml
            git config user.email jenkins@ci && git config user.name jenkins
            git commit -am "ci: deploy build $BUILD_NUMBER [skip ci]"
            git push https://$U:$P@github.com/MohamedMosalm/URLShortner.git HEAD:main
          '''
        }
      }
    }
  }
}
