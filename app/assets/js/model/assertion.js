define(["util/Logger", "util/Util", "model/model"], function (Logger, Util, Model) {

    "use strict";

    var logger = Logger.of("Assertion"),
        Assertion;

    Assertion = Model.extend({

        defaults: {
            assertor: "none",
            severity: "info",
            title: "",
            description: null,
            occurrences: 0,
            occurrencesLegend: "",
            contexts: [],
            resources: [],
            contextsMore: 0,
            resourcesMore: 0
        },

        search: function (search) {
            return this.get("title").toLowerCase().indexOf(search.toLowerCase()) > -1;
        }

    });

    Assertion.View = Assertion.View.extend({

        templateId: "assertion-template",

        init: function () {
            this.el.setAttribute("data-id", this.model.id);
        },

        events: {
            "click .title": "fold"
        },

        fold: function (event) {
            if (this.isFoldable()) {
                $(event.currentTarget).parents("article").toggleClass("folded");
            }
        },

        isFoldable: function () {
            /*console.log("is null?")
            console.log(!_.isNull(this.model.get("description")));
            console.log("has contexts?")
            console.log(this.model.get("contexts").length > 0);
            console.log("has resources?")
            console.log(this.model.get("resources").length > 0);*/
            return !_.isNull(this.model.get("description")) ||
                    this.model.get("contexts").length > 0 ||
                    this.model.get("resources").length > 0;
        }

    });

    Assertion.fromHtml = function ($article) {
        var value = Util.valueFrom($article);
        return {
            id: $article.attr("data-id"),
            assertor: value('data-assertor'),
            severity: value('data-severity'),
            title: value('data-title'),
            description: value('data-description') || null,
            occurrences: parseInt(value('data-occurrences'), 10),
            occurrencesLegend: value('data-occurrencesLegend'),
            contexts: $('.context', $article).map(function (i, context) {
                var value = Util.valueFrom($(context));
                return {
                    line: value('data-context-line'),
                    column: value('data-context-column'),
                    content: value('data-context-content')
                };
            }),
            resources: $('.resource', $article).map(function (i, resource) {
                var value = Util.valueFrom($(resource));
                return { url: value('data-resource'), c: value('data-count') };
            }),
            contextsMore: parseInt(value('data-contextsMore'), 10) || 0,
            resourcesMore: parseInt(value('data-resourcesMore'), 10) || 0
        };
    };

    return Assertion;

});