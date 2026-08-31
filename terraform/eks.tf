resource "aws_ecr_repository" "clausis_repo" {
  name                 = "clausis-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}
