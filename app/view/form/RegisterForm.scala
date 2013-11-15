package org.w3.vs.view.form

import play.api.data.Forms._
import play.api.data._
import org.w3.vs.view._
import controllers.routes

case class Register(
  name: String = "",
  email: String = "",
  password: String = "",
  password2: String = "",
  optedIn: Boolean = false,
  redirectUri: String = routes.Jobs.index().url)

object RegisterForm {

  def apply(): Form[Register] = Form(
    mapping(
      "userName" -> nonEmptyText,
      "r_email" -> email,
      "r_password" -> nonEmptyText(minLength = 6),
      "repeatPassword" -> text,
      "optedIn" -> of[Boolean](checkboxFormatter),
      "uri" -> text
    )(Register.apply)(Register.unapply).verifying("password.dont_match", p => p.password == p.password2)
  ).fill(Register())

}

  /*type RegisterType = (String, String, String, String, Boolean, String)

  def bind()(implicit req: Request[_], context: ExecutionContext): Either[RegisterForm, ValidRegisterForm] = {
    val form = playForm.bindFromRequest
    form.fold(
      f => Left(new RegisterForm(f)),
      s => Right(new ValidRegisterForm(form, s))
    )
  }

  def blank: RegisterForm = new RegisterForm(playForm)

  def redirectTo(uri: String) = new RegisterForm(playForm.fill("", "", "", "", false, uri))

  private def playForm: Form[RegisterType] = Form(
    tuple(
      "userName" -> nonEmptyText,
      "r_email" -> email,
      "r_password" -> nonEmptyText(minLength = 6),
      "repeatPassword" -> text,
      "optedIn" -> of[Boolean](checkboxFormatter),
      //"terms" -> of[Boolean](checkboxFormatter).verifying("not_accepted", _ == true),
      "uri" -> text
    ).verifying("password.dont_match", p => p._3 == p._4)
  )

}

class RegisterForm private[view](val form: Form[RegisterType]) extends VSForm {
  def apply(s: String) = form(s)

  def withError(key: String, message: String) = new RegisterForm(form = form.withError(key, message))
  def withGlobalError(message: String) = {
    new RegisterForm(form = form.withGlobalError(message))
  }

  def globalErrors: Seq[(String, String)] = {
    form.globalErrors.map{
      case error => ("error", Messages(error.message))
    }
  }

  def errors: Seq[(String, String)] = {
    form.errors.map{
      case error => ("error", Messages("form." + error.key + "." + error.message))
    }
  }
}

class ValidRegisterForm private[view](form: Form[RegisterType], bind: RegisterType) extends RegisterForm(form) with VSForm {
  val (name, email, password, repeatPassword, optedIn, redirectUri) = bind
}   */
