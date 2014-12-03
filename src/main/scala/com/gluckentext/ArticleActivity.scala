package com.gluckentext

import android.content.{BroadcastReceiver, IntentFilter}
import android.graphics.Color
import android.os.{Bundle, AsyncTask}
import android.text.Html
import android.view.{MenuItem, Menu, ActionMode}
import android.webkit.{WebView, WebChromeClient, WebViewClient, WebSettings}
import com.gluckentext.QuizHtml._
import org.scaloid.common._
import Serializer._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}

class ArticleActivity extends SActivity {

  implicit val tag = LoggerTag("Gluckentext")
  implicit val exec = ExecutionContext.fromExecutor(AsyncTask.THREAD_POOL_EXECUTOR)

  val quizSubject = List("of", "in", "at", "on")
  var webViewOption: Option[SWebView] = None

  onCreate {
    contentView = new SRelativeLayout {
      val loadButton = SButton(R.string.load).<<.wrap.alignParentRight.alignParentLeft.alignParentTop.>>.onClick(loadArticle())
      val webView: SWebView = SWebView().<<.wrap.alignParentRight.alignParentLeft.alignParentBottom.below(loadButton).>>
      webViewOption = Some(webView)
    }
  }

  onResume {
    val quiz = getPersistedQuiz
    quiz match {
      case None => loadArticle()
      case Some(quiz) =>
        val quizText = generateQuizHtml(quiz)
        populateWebView(quizText)
    }
  }

  def populateWebView(quizHtml: String) = {
    webViewOption match {
      case Some(webView) =>
        prepareWebView(webView)
        webView.loadData(quizHtml, "text/html; charset=UTF-8", null)
      case None => toast("Looks like the webview is missing")
    }
  }

  def loadArticle() = {
    val f = Future {
      val article = WikiPageLoader.loadWikiPageXml("en", "Standard_Chinese")
      val quiz = createQuiz about quizSubject from article
      val quizText = generateQuizHtml(quiz)
      runOnUiThread {
        persistQuiz(quiz)
        populateWebView(quizText)
      }
    }
    f.onFailure { case x => x.printStackTrace()}
  }

  def prepareWebView(webView: SWebView) {
    webView.settings.setJavaScriptEnabled(true)
    webView.setWebChromeClient(new WebChromeClient)
    webView.webViewClient = new WebViewClient {
      override def shouldOverrideUrlLoading(view: WebView, url: String) = {
        quizWordClicked(url.replaceAll("\\?", ""))
        true
      }
    }
  }

  def quizWordClicked(url: String) = {
    info("Quiz word clicked: " + url)
    url match {
      case makeGuessUrl(quizWord) =>
        startActionMode(new ActionMode.Callback {
          override def onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean = {
            quizSubject.foreach(guess => menu.add(guess).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS))
            true
          }

          override def onActionItemClicked(actionMode: ActionMode, menuItem: MenuItem): Boolean = {
            guessClicked(quizWord, menuItem.getTitle.toString)
            actionMode.finish()
            true
          }

          override def onDestroyActionMode(actionMode: ActionMode): Unit = {}

          override def onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean = true
        })
    }
  }

  def guessClicked(word: QuizWord, guess: String) = {
    if (word.rightAnswer == guess) markRightAnswer(word)
    else toast("This is not the right answer. Want to try again?")
  }

  def markRightAnswer(answeredWord: QuizWord) {
    val jsUrl = "javascript:markSolved(" + getTagId(answeredWord) + ")"
    webViewOption match {
      case Some(webView) => webView.loadUrl(jsUrl)
    }
    getPersistedQuiz match {
      case Some(quiz) =>
        val quizWithSolvedWord = quiz.map {
          case w@QuizWord(answeredWord.id, _, _) => w.solved
          case x => x
        }
        persistQuiz(quizWithSolvedWord)
      case None => toast("Looks like the quiz object is missing from the app prefs")
    }
  }

  def getPersistedQuiz: Option[Iterable[QuizPart]] = {
    Preferences().quiz("") match {
      case "" => None
      case q => Some(deserializeToQuiz(q))
    }
  }

  def persistQuiz(quiz: Iterable[QuizPart]) = {
    val quizSerialized: String = serializeQuiz(quiz)
    Preferences().quiz = quizSerialized
  }
}

