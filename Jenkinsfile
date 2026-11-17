pipeline {
  agent any
  environment { IMAGE = 'docker.io/mohamedmosalm/url-shortener' }
  stages {
    stage('Build and push') {
      when { not { changelog '.*\\[skip ci\\].*' } }
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'U', passwordVariable: 'P')]) {
          sh '''
            sed -i "s#image: .*#image: $IMAGE:$BUILD_NUMBER#" k8s/app.yaml
            git diff --quiet && exit 0
            git config user.email jenkins@ci && git config user.name jenkins
            git commit -am "ci: deploy build $BUILD_NUMBER [skip ci]"
            git push https://$U:$P@github.com/MohamedMosalm/URLShortner.git HEAD:main
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
