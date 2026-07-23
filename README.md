# Yeolo-BE
제로터치 초개인화 여행 플랫폼 여로

## 배포 (dev/EKS)

k8s 매니페스트(Deployment/Service/Ingress 등)는 **Yeolo-Infra** repo가 소유한다.
BE repo는 매니페스트를 두지 않는다. CD(`.github/workflows/deploy.yml`)는 이미지 태그만 교체한다.

앱 환경변수는 **repo에 커밋하지 않고** `was-secrets` Secret으로 클러스터에 직접 주입한다
(비밀/비밀 아님 구분 없이 통합). DB 자격증명(`DB_USERNAME`/`DB_PASSWORD`)만 예외로
인프라가 ESO로 동기화하는 `db-credentials` Secret에서 온다.

`was-secrets`는 한 번만 생성한다(값은 실제 값으로 교체, 커밋 금지):

```bash
kubectl -n app create secret generic was-secrets \
  --from-literal=JWT_SECRET='<32바이트+ 랜덤>' \
  --from-literal=GOOGLE_CLIENT_ID='<google client id>' \
  --from-literal=GOOGLE_CLIENT_SECRET='<google client secret>' \
  --from-literal=GOOGLE_REDIRECT_URI='' \
  --from-literal=DB_URL='jdbc:postgresql://<RDS_ENDPOINT>:5432/yeolo' \
  --from-literal=JPA_DDL_AUTO='update' \
  --from-literal=AI_BASE_URL='http://ai.app.svc.cluster.local:80' \
  --from-literal=AI_INTERNAL_API_KEY='' \
  --from-literal=AI_COURSE_PROVIDER='stub' \
  --from-literal=GEOCODE_PROVIDER='stub' \
  --dry-run=client -o yaml | kubectl apply -f -
```

값 갱신 후 반영: `kubectl -n app rollout restart deployment/was`
(env 키 정의는 `src/main/resources/application.properties`의 `${VAR}` 참조 참고.)
